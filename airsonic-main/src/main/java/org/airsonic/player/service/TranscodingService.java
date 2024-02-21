/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2023-2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import com.google.common.io.MoreFiles;
import org.airsonic.player.controller.VideoPlayerController;
import org.airsonic.player.domain.*;
import org.airsonic.player.io.TranscodeInputStream;
import org.airsonic.player.repository.PlayerRepository;
import org.airsonic.player.repository.TranscodingRepository;
import org.airsonic.player.util.StringUtil;
import org.airsonic.player.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Provides services for transcoding media. Transcoding is the process of
 * converting an audio stream to a different format and/or bit rate. The latter is
 * also called downsampling.
 *
 * @author Sindre Mehus
 * @see TranscodeInputStream
 */
@Service
public class TranscodingService {

    private static final Logger LOG = LoggerFactory.getLogger(TranscodingService.class);
    public static final String FORMAT_RAW = "raw";

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private PlayerRepository playerRepository;;
    @Autowired
    private TranscodingRepository transcodingRepository;
    @Autowired
    private PersonalSettingsService personalSettingsService;

    /**
     * Returns all transcodings.
     *
     * @return Possibly empty list of all transcodings.
     */
    public List<Transcoding> getAllTranscodings() {
        return transcodingRepository.findAll();
    }

    /**
     * Returns all active transcodings for the given player. Only enabled transcodings are returned.
     *
     * @param player The player.
     * @return All active transcodings for the player.
     */
    public List<Transcoding> getTranscodingsForPlayer(Player player) {
        return transcodingRepository.findByPlayersContaining(player);
    }

    /**
     * Sets the list of active transcodings for the given player.
     *
     * @param player         The player.
     * @param transcodingIds ID's of the active transcodings.
     */
    @Transactional
    public void setTranscodingsForPlayerByIds(Player player, List<Integer> transcodingIds) {
        List<Transcoding> transcodings = transcodingRepository.findByIdIn(transcodingIds);
        setTranscodingsForPlayer(player, transcodings);
    }

    /**
     * Sets the list of active transcodings for the given player.
     *
     * @param player       The player.
     * @param transcodings The active transcodings.
     */
    @Transactional
    public void setTranscodingsForPlayer(Player player, List<Transcoding> transcodings) {
        player.setTranscodings(transcodings);
        playerRepository.save(player);
    }

    /**
     * Creates a new transcoding.
     *
     * @param transcoding The transcoding to create.
     */
    @Transactional
    public void createTranscoding(Transcoding transcoding) {
        // Activate this transcoding for all players?
        transcodingRepository.save(transcoding);
        if (transcoding.isDefaultActive()) {
            List<Player> players = playerRepository.findAll();
            players.forEach(player -> player.addTranscoding(transcoding));
            playerRepository.saveAll(players);
        }
    }

    /**
     * Deletes the transcoding with the given ID.
     *
     * @param id The transcoding ID.
     */
    @Transactional
    public void deleteTranscoding(Integer id) {
        transcodingRepository.deleteById(id);
    }

    /**
     * Updates the given transcoding.
     *
     * @param transcoding The transcoding to update.
     */
    @Transactional
    public void updateTranscoding(Transcoding transcoding) {
        transcodingRepository.save(transcoding);
    }

    /**
     * Updates the given transcoding.
     * @param id The transcoding ID.
     * @param name The transcoding name.
     * @param sourceFormats The source formats.
     * @param targetFormat The target format.
     * @param step1 The first step.
     * @param step2 The second step.
     * @param defaultActive Whether the transcoding is active by default.
     */
    @Transactional
    public void updateTranscoding(Integer id, String name, String sourceFormats, String targetFormat, String step1,
            String step2, boolean defaultActive) {
        transcodingRepository.findById(id).ifPresentOrElse(t -> {
            t.setName(name);
            t.setSourceFormats(sourceFormats);
            t.setTargetFormat(targetFormat);
            t.setStep1(step1);
            t.setStep2(step2);
            t.setDefaultActive(defaultActive);
            transcodingRepository.save(t);
        }, () -> {
                LOG.warn("Transcoding with id {} not found", id);
            }
        );
    }
    /**
     * Returns whether transcoding is required for the given media file and player combination.
     *
     * @param mediaFile The media file.
     * @param player    The player.
     * @return Whether transcoding  will be performed if invoking the
     *         {@link #getTranscodedInputStream} method with the same arguments.
     */
    public boolean isTranscodingRequired(MediaFile mediaFile, Player player) {
        return getTranscoding(mediaFile, player, null, false) != null;
    }

    /**
     * Returns the suffix for the given player and media file, taking transcodings into account.
     *
     * @param player                The player in question.
     * @param file                  The media file.
     * @param preferredTargetFormat Used to select among multiple applicable transcodings. May be {@code null}.
     * @return The file suffix, e.g., "mp3".
     */
    public String getSuffix(Player player, MediaFile file, String preferredTargetFormat) {
        Transcoding transcoding = getTranscoding(file, player, preferredTargetFormat, false);
        return transcoding != null ? transcoding.getTargetFormat() : file.getFormat();
    }

    /**
     * Creates parameters for a possibly transcoded, downsampled or split input stream for the given media file and player combination.
     *
     * A transcoding is applied if it is applicable for the format of the given file, and is activated for the
     * given player and either the desired format or bitrate needs changing or only a section of the file should be
     * returned.
     *
     * If no transcoding is applicable, the file may still be downsampled, given that the player is configured
     * with a bit rate limit which is higher than the actual bit rate of the file.
     *
     * If neither transcoding nor downsampling is needed the file might still be split in case of indexed tracks.
     *
     * Otherwise, a normal input stream to the original file is returned.
     *
     * @param mediaFile                The media file.
     * @param player                   The player.
     * @param maxBitRate               Overrides the per-player and per-user bitrate limit. May be {@code null}.
     * @param preferredTargetFormat    Used to select among multiple applicable transcodings. May be {@code null}.
     * @param videoTranscodingSettings Parameters used when transcoding video. May be {@code null}.
     * @return Parameters to be used in the {@link #getTranscodedInputStream} method.
     */
    public Parameters getParameters(MediaFile mediaFile, Player player, Integer maxBitRate, String preferredTargetFormat,
                                    VideoTranscodingSettings videoTranscodingSettings) {

        Parameters parameters = new Parameters(mediaFile, videoTranscodingSettings);
        String suffix = mediaFile.getFormat();

        TranscodeScheme transcodeScheme = getTranscodeScheme(player);
        if (maxBitRate == null && transcodeScheme != TranscodeScheme.OFF) {
            maxBitRate = transcodeScheme.getMaxBitRate();
        }

        boolean hls = videoTranscodingSettings != null && videoTranscodingSettings.getHlsSegmentFilename() != null;
        Transcoding transcoding = getTranscoding(mediaFile, player, preferredTargetFormat, hls);
        if (transcoding != null) {
            parameters.setTranscoding(transcoding);
            if (maxBitRate == null) {
                maxBitRate = mediaFile.isVideo() ? VideoPlayerController.DEFAULT_BIT_RATE : TranscodeScheme.MAX_192.getMaxBitRate();
            }
        } else if (maxBitRate != null) {
            boolean supported = isDownsamplingSupported(mediaFile);
            Integer bitRate = mediaFile.getBitRate();
            if (supported && bitRate != null && bitRate > maxBitRate) {
                parameters.setTranscoding(new Transcoding(null, "downsample", suffix, suffix, settingsService.getDownsamplingCommand(), null, null, true));
            }
        }

        // Insert split command for indexed tracks without active transcodings
        if (mediaFile.isIndexedTrack() && parameters.getTranscoding() == null) {
            parameters.setTranscoding(new Transcoding(null, "split", suffix, suffix, settingsService.getSplitCommand(), null, null, true));
        }

        parameters.setMaxBitRate(maxBitRate);
        parameters.setExpectedLength(getExpectedLength(parameters));
        parameters.setRangeAllowed(isRangeAllowed(parameters));
        return parameters;
    }

    /**
     * Returns a possibly transcoded, downsampled and/or split input stream for the given music file and player combination.
     *
     * A transcoding is applied if it is applicable for the format of the given file, and is activated for the
     * given player.
     *
     * If no transcoding is applicable, the file may still be downsampled, given that the player is configured
     * with a bit rate limit which is higher than the actual bit rate of the file.
     *
     * If neither transcoding nor downsampling is needed the file might still be split in case of indexed tracks.
     *
     * Otherwise, a normal input stream to the original file is returned.
     *
     * @param parameters As returned by {@link #getParameters}.
     * @return A possible transcoded or downsampled input stream.
     * @throws IOException If an I/O error occurs.
     */
    public InputStream getTranscodedInputStream(Parameters parameters) throws IOException {
        try {

            if (parameters.getTranscoding() != null) {
                return createTranscodedInputStream(parameters);
            }

        } catch (IOException x) {
            LOG.warn("Transcoder failed for {} in folder {}. Using original file", parameters.getMediaFile().getPath(), parameters.getMediaFile().getFolder().getId(), x);
        } catch (Exception x) {
            LOG.warn("Transcoder failed for {} in folder {}. Using original file", parameters.getMediaFile().getPath(), parameters.getMediaFile().getFolder().getId(), x);
        }

        return new BufferedInputStream(Files.newInputStream(parameters.getMediaFile().getFullPath().toAbsolutePath()));
    }

    /**
     * Returns the strictest transcoding scheme defined for the player and the user.
     */
    private TranscodeScheme getTranscodeScheme(Player player) {
        if (player == null) {
            return TranscodeScheme.OFF;
        }
        String username = player.getUsername();
        if (username != null) {
            UserSettings userSettings = personalSettingsService.getUserSettings(username);
            return player.getTranscodeScheme().strictest(userSettings.getTranscodeScheme());
        }

        return player.getTranscodeScheme();
    }

    /**
     * Returns an input stream by applying the given transcoding to the given music file.
     *
     * @param parameters Transcoding parameters.
     * @return The transcoded input stream.
     * @throws IOException If an I/O error occurs.
     */
    private InputStream createTranscodedInputStream(Parameters parameters)
            throws IOException {

        Transcoding transcoding = parameters.getTranscoding();
        Integer maxBitRate = parameters.getMaxBitRate();
        VideoTranscodingSettings videoTranscodingSettings = parameters.getVideoTranscodingSettings();
        MediaFile mediaFile = parameters.getMediaFile();

        TranscodeInputStream in = createTranscodeInputStream(transcoding.getStep1(), maxBitRate, videoTranscodingSettings, mediaFile, null);

        if (transcoding.getStep2() != null) {
            in = createTranscodeInputStream(transcoding.getStep2(), maxBitRate, videoTranscodingSettings, mediaFile, in);
        }

        if (transcoding.getStep3() != null) {
            in = createTranscodeInputStream(transcoding.getStep3(), maxBitRate, videoTranscodingSettings, mediaFile, in);
        }

        return in;
    }

    /**
     * Creates a transcoded input stream by interpreting the given command line string.
     * This includes the following:
     * <ul>
     * <li>Splitting the command line string to an array.</li>
     * <li>Replacing occurrences of "%s" with the path of the given music file.</li>
     * <li>Replacing occurrences of "%t" with the title of the given music file.</li>
     * <li>Replacing occurrences of "%l" with the album name of the given music file.</li>
     * <li>Replacing occurrences of "%a" with the artist name of the given music file.</li>
     * <li>Replacing occurrences of "%b" with the max bitrate.</li>
     * <li>Replacing occurrences of "%f" with the input file format (used for indexed tracks)</li>
     * <li>Replacing occurrences of "%o" with the time offset (used for scrubbing video and indexed tracks).</li>
     * <li>Replacing occurrences of "%d" with the duration (used for HLS and indexed tracks).</li>
     * <li>Replacing occurrences of "%w" with the video image width.</li>
     * <li>Replacing occurrences of "%h" with the video image height.</li>
     * <li>Prepending the path of the transcoder directory if the transcoder is found there.</li>
     * </ul>
     *
     * @param command                  The command line string.
     * @param maxBitRate               The maximum bitrate to use. May not be {@code null}.
     * @param videoTranscodingSettings Parameters used when transcoding video. May be {@code null}.
     * @param mediaFile                The media file.
     * @param in                       Data to feed to the process.  May be {@code null}.
     * @return The newly created input stream.
     */
    private TranscodeInputStream createTranscodeInputStream(String command, Integer maxBitRate,
                                                            VideoTranscodingSettings videoTranscodingSettings,
                                                            MediaFile mediaFile, InputStream in) throws IOException {

        // Work-around for filename character encoding problem on Windows.
        // Create temporary file, and feed this to the transcoder.
        Path path = mediaFile.getFullPath().toAbsolutePath();
        String pathString = path.toString();
        Path tmpFile = null;
        if (Util.isWindows() && !mediaFile.isVideo() && !StringUtils.isAsciiPrintable(path.toString()) && StringUtils.contains(command, "%s")) {
            tmpFile = Files.createTempFile("airsonic", "." + MoreFiles.getFileExtension(path));
            tmpFile.toFile().deleteOnExit();
            Files.copy(path, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Created tmp file: {}", tmpFile);
            pathString = tmpFile.toString();
        }

        // insert split sequence for indexed tracks
        command = command.replace("%S", (mediaFile.isIndexedTrack()) ? settingsService.getSplitOptions() : "");

        Map<String, String> vars = generateTranscodingSubstitutionMap(
                Optional.ofNullable(mediaFile.getTitle()).orElse("Unknown Media"),
                Optional.ofNullable(mediaFile.getArtist()).orElse("Unknown Artist"),
                Optional.ofNullable(mediaFile.getAlbumName()).orElse("Unknown Album"),
                Optional.ofNullable(maxBitRate).map(String::valueOf).orElse(null),
                Optional.ofNullable(mediaFile.getFormat()).orElse(null),
                Optional.ofNullable(videoTranscodingSettings).map(VideoTranscodingSettings::getTimeOffset).map(String::valueOf).orElse(String.valueOf(mediaFile.getStartPosition())),
                Optional.ofNullable(videoTranscodingSettings).map(VideoTranscodingSettings::getDuration).map(String::valueOf).orElse(String.valueOf(mediaFile.getDuration())),
                Optional.ofNullable(videoTranscodingSettings).map(VideoTranscodingSettings::getWidth).map(String::valueOf).orElse(null),
                Optional.ofNullable(videoTranscodingSettings).map(VideoTranscodingSettings::getHeight).map(String::valueOf).orElse(null),
                Optional.ofNullable(maxBitRate).map(TranscodingService::getAverageVideoBitRate).map(String::valueOf).orElse(null),
                Optional.ofNullable(maxBitRate).map(TranscodingService::getSuitableAudioBitRate).map(String::valueOf).orElse(null),
                Optional.ofNullable(videoTranscodingSettings).map(VideoTranscodingSettings::getAudioTrackIndex).map(String::valueOf).orElse(null),
                Optional.ofNullable(videoTranscodingSettings).map(VideoTranscodingSettings::getHlsSegmentIndex).map(String::valueOf).orElse(null),
                Optional.ofNullable(videoTranscodingSettings).map(VideoTranscodingSettings::getHlsSegmentFilename).orElse(null),
                pathString,
                // TODO: this shouldn't be part of videosettings
                Optional.ofNullable(videoTranscodingSettings).map(VideoTranscodingSettings::getOutputFilename).orElse(null));

        ProcessBuilder builder = transformTranscodingVariables(command, vars);
        return new TranscodeInputStream(builder, in, tmpFile);
    }

    public ProcessBuilder transformTranscodingVariables(String command, Map<String, String> vars) {
        String[] splitCommand = StringUtil.split(command);
        List<String> kl = new ArrayList<>(vars.size());
        List<String> vl = new ArrayList<>(vars.size());
        vars.entrySet().stream().filter(e -> e.getValue() != null).forEach(e -> {
            kl.add(e.getKey());
            vl.add(e.getValue());
        });
        String[] ka = kl.toArray(new String[0]);
        String[] va = vl.toArray(new String[0]);
        List<String> result = IntStream.range(0, splitCommand.length)
                .mapToObj(i -> substituteTranscodingVariable(i, splitCommand[i], ka, va)).collect(Collectors.toList());

        return new ProcessBuilder(result);
    }

    public String substituteTranscodingVariable(int index, String commandSegment, String[] keys, String[] values) {
        if (index == 0) {
            return settingsService.resolveTranscodeExecutable(commandSegment, commandSegment);
        }

        return StringUtils.replaceEach(commandSegment, keys, values);
    }

    public static Map<String, String> generateTranscodingSubstitutionMap(
            String title,
            String artist,
            String album,
            String maxVideoBitRate,
            String format,
            String timeOffset,
            String duration,
            String width,
            String height,
            String averageVideoRate,
            String audioRate,
            String audioTrackIndex,
            String hlsSegmentIndex,
            String hlsSegmentFilename,
            String inputFilename,
            String outputFilename) {
        Map<String, String> result = new HashMap<>();
        result.put("%t", title);
        result.put("%a", artist);
        result.put("%l", album);

        result.put("%f", format);
        result.put("%b", maxVideoBitRate);
        result.put("%o", timeOffset);
        result.put("%d", duration);
        result.put("%w", width);
        result.put("%h", height);

        result.put("%v", averageVideoRate);
        result.put("%r", audioRate);
        result.put("%i", audioTrackIndex);
        result.put("%j", hlsSegmentIndex);
        result.put("%n", hlsSegmentFilename);
        result.put("%s", inputFilename);
        result.put("%p", outputFilename);

        return result;
    }

    /**
     * Returns an applicable transcoding for the given file and player, or <code>null</code> if no
     * transcoding should be done.
     */
    private Transcoding getTranscoding(MediaFile mediaFile, Player player, String preferredTargetFormat, boolean hls) {
        Transcoding transcoding = null;
        String suffix = mediaFile.getFormat();

        if (hls) {
            // HLS can not be combined with cue-indexed files
            transcoding = new Transcoding(null, "hls", mediaFile.getFormat(), "ts", settingsService.getHlsCommand(), null, null, true);
        } else {
            if (FORMAT_RAW.equals(preferredTargetFormat)) {
                // RAW and cue-indexed files can be combined since the split command does not re-encode audio or video
                transcoding = null;
            } else {

                List<Transcoding> applicableTranscodings = new LinkedList<Transcoding>();

                List<Transcoding> transcodingsForPlayer = getTranscodingsForPlayer(player);
                for (Transcoding tr : transcodingsForPlayer) {
                    // special case for now as video must have a transcoding
                    if (mediaFile.isVideo() && tr.getTargetFormat().equalsIgnoreCase(preferredTargetFormat) && isTranscodingInstalled(tr)) {
                        LOG.debug("Detected source to target format match for video");
                        applicableTranscodings.add(tr);
                        break;
                    }
                    for (String sourceFormat : tr.getSourceFormatsAsArray()) {
                        if (sourceFormat.equalsIgnoreCase(suffix) && isTranscodingInstalled(tr)) {
                            applicableTranscodings.add(tr);
                        }
                    }
                }

                if (!applicableTranscodings.isEmpty()) {
                    for (Transcoding tr : applicableTranscodings) {
                        if (tr.getTargetFormat().equalsIgnoreCase(preferredTargetFormat)) {
                            transcoding = tr;
                            break;
                        }
                    }

                    if (transcoding == null) {
                        transcoding = applicableTranscodings.get(0);
                    }
                }
            }
        }

        return transcoding;
    }

    /**
     * Returns whether downsampling is supported (i.e., whether ffmpeg is installed or not.)
     *
     * @param mediaFile If not null, returns whether downsampling is supported for this file.
     * @return Whether downsampling is supported.
     */
    public boolean isDownsamplingSupported(MediaFile mediaFile) {
        if (mediaFile != null) {
            boolean isMp3 = "mp3".equalsIgnoreCase(mediaFile.getFormat());
            if (!isMp3) {
                return false;
            }
        }

        String commandLine = settingsService.getDownsamplingCommand();
        return isTranscodingStepInstalled(commandLine);
    }

    private boolean isTranscodingInstalled(Transcoding transcoding) {
        return isTranscodingStepInstalled(transcoding.getStep1()) &&
                isTranscodingStepInstalled(transcoding.getStep2()) &&
                isTranscodingStepInstalled(transcoding.getStep3());
    }

    private boolean isTranscodingStepInstalled(String step) {
        if (StringUtils.isEmpty(step)) {
            return true;
        }
        String executable = StringUtil.split(step)[0];
        return settingsService.resolveTranscodeExecutable(executable, null) != null;
    }

    /**
     * Returns the length (or predicted/expected length) of a (possibly padded) media stream
     */
    private Long getExpectedLength(Parameters parameters) {
        MediaFile file = parameters.getMediaFile();

        if (!parameters.isTranscode()) {
            return file.getFileSize();
        }
        Double duration = file.getDuration();
        Integer maxBitRate = parameters.getMaxBitRate();

        if (duration == null) {
            LOG.warn("Unknown duration for {}. Unable to estimate transcoded size.", file);
            return null;
        }

        if (maxBitRate == null) {
            LOG.error("Unknown bit rate for {}. Unable to estimate transcoded size.", file);
            return null;
        }

        // Over-estimate size a bit (a custom time surplus plus a custom byte surplus) so don't cut off early in case of small calculation differences
        return Math.round((duration + (settingsService.getTranscodeEstimateTimePadding() / 1000.0)) * maxBitRate * 1000L / 8L)
                + settingsService.getTranscodeEstimateBytePadding();
    }

    private boolean isRangeAllowed(Parameters parameters) {
        Transcoding transcoding = parameters.getTranscoding();
        List<String> steps = Arrays.asList();
        if (transcoding != null) {
            steps = Arrays.asList(transcoding.getStep3(), transcoding.getStep2(), transcoding.getStep1());
        } else {
            return true;  // no active transcodings
        }

        // Verify that were able to predict the length
        if (parameters.getExpectedLength() == null) {
            return false;
        }

        // Check if last configured step uses the bitrate, if so, range should be pretty safe
        for (String step : steps) {
            if (step != null) {
                return step.contains("%b");
            }
        }
        return false;
    }

    public static Dimension getSuitableVideoSize(Integer existingWidth, Integer existingHeight, int peakVideoBitRate) {
        int w;
        if (peakVideoBitRate < 400) {
            w = 416;
        } else if (peakVideoBitRate < 800) {
            w = 480;
        } else if (peakVideoBitRate < 1200) {
            w = 640;
        } else if (peakVideoBitRate < 2200) {
            w = 768;
        } else if (peakVideoBitRate < 3300) {
            w = 960;
        } else if (peakVideoBitRate < 8600) {
            w = 1280;
        } else {
            w = 1920;
        }
        int h = even(w * 9 / 16);
        if (existingWidth == null || existingHeight == null)
            return new Dimension(w, h);
        if (existingWidth.intValue() < w || existingHeight.intValue() < h)
            return new Dimension(even(existingWidth.intValue()), even(existingHeight.intValue()));
        double aspectRatio = existingWidth.doubleValue() / existingHeight.doubleValue();
        h = (int) Math.round(w / aspectRatio);
        return new Dimension(even(w), even(h));
    }

    private static int even(int size) {
        return size + size % 2;
    }

    public static int getSuitableAudioBitRate(Integer peakVideoBitRate) {
        if (peakVideoBitRate < 1200)
            return 64;
        if (peakVideoBitRate < 5000)
            return 96;
        return 128;
    }

    public static int getAverageVideoBitRate(Integer peakVideoBitRate) {
        switch (peakVideoBitRate) {
            case 200:
                return 145;
            case 400:
                return 365;
            case 800:
                return 730;
            case 1200:
                return 1100;
            case 2200:
                return 2000;
            case 3300:
                return 3000;
            case 5000:
                return 4500;
            case 6500:
                return 6000;
        }
        return (int) (peakVideoBitRate * 0.9D);
    }

    public static class Parameters {
        private Long expectedLength;
        private boolean rangeAllowed;
        private final MediaFile mediaFile;
        private final VideoTranscodingSettings videoTranscodingSettings;
        private Integer maxBitRate;
        private Transcoding transcoding;

        public Parameters(MediaFile mediaFile, VideoTranscodingSettings videoTranscodingSettings) {
            this.mediaFile = mediaFile;
            this.videoTranscodingSettings = videoTranscodingSettings;
        }

        public Parameters(MediaFile mediaFile, VideoTranscodingSettings videoTranscodingSettings, Integer maxBitrate) {
            this(mediaFile, videoTranscodingSettings);
            this.maxBitRate = maxBitrate;
        }

        public void setMaxBitRate(Integer maxBitRate) {
            this.maxBitRate = maxBitRate;
        }

        public boolean isTranscode() {
            return transcoding != null;
        }

        public boolean isRangeAllowed() {
            return this.rangeAllowed;
        }

        public void setRangeAllowed(boolean rangeAllowed) {
            this.rangeAllowed = rangeAllowed;
        }

        public Long getExpectedLength() {
            return this.expectedLength;
        }

        public void setExpectedLength(Long expectedLength) {
            this.expectedLength = expectedLength;
        }

        public void setTranscoding(Transcoding transcoding) {
            this.transcoding = transcoding;
        }

        public Transcoding getTranscoding() {
            return transcoding;
        }

        public MediaFile getMediaFile() {
            return mediaFile;
        }

        public Integer getMaxBitRate() {
            return maxBitRate;
        }

        public VideoTranscodingSettings getVideoTranscodingSettings() {
            return videoTranscodingSettings;
        }
    }
}
