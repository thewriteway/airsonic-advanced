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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.domain;

import org.apache.commons.lang.StringUtils;

import java.nio.file.Files;
import java.util.*;
import java.util.stream.IntStream;

/**
 * A play queue is a list of music files that are associated to a remote player.
 *
 * @author Sindre Mehus
 */
public class PlayQueue {

    private List<MediaFile> files = new ArrayList<>();
    private RepeatStatus repeatStatus = RepeatStatus.OFF;
    private String name = "(unnamed)";
    private Status status = Status.PLAYING;

    private RandomSearchCriteria randomSearchCriteria;
    private InternetRadio internetRadio;

    public PlayQueue() {
    }

    /**
     * The index of the current song, or -1 if the end of the playlist is reached.
     * Note that both the index and the playlist size can be zero.
     */
    private volatile int index = 0;

    /**
     * Used for undo functionality.
     */
    private List<MediaFile> filesBackup = new ArrayList<>();
    private int indexBackup = 0;

    /**
     * Returns the user-defined name of the playlist.
     *
     * @return The name of the playlist, or <code>null</code> if no name has been assigned.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user-defined name of the playlist.
     *
     * @param name The name of the playlist.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the current song in the playlist.
     *
     * @return The current song in the playlist, or <code>null</code> if no current song exists.
     */
    public synchronized MediaFile getCurrentFile() {
        if (index == -1 || index == 0 && size() == 0) {
            setStatus(Status.STOPPED);
            return null;
        } else {
            MediaFile file = files.get(index);

            // Remove file from playlist if it doesn't exist.
            if (!Files.exists(file.getFullPath())) {
                files.remove(index);
                index = Math.max(0, Math.min(index, size() - 1));
                return getCurrentFile();
            }

            return file;
        }
    }

    /**
     * Returns all music files in the playlist.
     *
     * @return All music files in the playlist.
     */
    public List<MediaFile> getFiles() {
        return files;
    }

    /**
     * Returns the music file at the given index.
     *
     * @param index The index.
     * @return The music file at the given index.
     * @throws IndexOutOfBoundsException If the index is out of range.
     */
    public synchronized MediaFile getFile(int index) {
        return files.get(index);
    }

    /**
     * Skip to the next song in the playlist.
     */
    public synchronized void next() {
        if (getRepeatStatus() == RepeatStatus.TRACK) {
            return;
        }

        index++;

        // Reached the end?
        if (index >= size()) {
            index = (getRepeatStatus() == RepeatStatus.QUEUE) ? 0 : -1;
        }
    }

    /**
     * Returns the number of songs in the playlists.
     *
     * @return The number of songs in the playlists.
     */
    public int size() {
        return files.size();
    }

    /**
     * Returns whether the playlist is empty.
     *
     * @return Whether the playlist is empty.
     */
    public boolean isEmpty() {
        return files.isEmpty();
    }

    /**
     * Returns the index of the current song.
     *
     * @return The index of the current song, or -1 if the end of the playlist is reached.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Sets the index of the current song.
     *
     * @param index The index of the current song.
     */
    public synchronized void setIndex(int index) {
        makeBackup();
        this.index = Math.max(0, Math.min(index, size() - 1));
        setStatus(Status.PLAYING);
    }

    /**
     * Adds one or more music file to the playlist.
     *
     * @param mediaFiles The music files to add.
     * @param index Where to add them.
     */
    public synchronized void addFilesAt(Collection<MediaFile> mediaFiles, int index) {
        makeBackup();
        files.addAll(index, mediaFiles);
        setStatus(Status.PLAYING);
    }

    /**
     * Adds one or more music file to the playlist.
     *
     * @param append     Whether existing songs in the playlist should be kept.
     * @param mediaFiles The music files to add.
     */
    public synchronized void addFiles(boolean append, Collection<MediaFile> mediaFiles) {
        makeBackup();
        if (!append) {
            index = 0;
            files.clear();
        }
        files.addAll(mediaFiles);
        setStatus(Status.PLAYING);
    }

    /**
     * Convenience method, equivalent to {@link #addFiles(boolean, Iterable)}.
     */
    public synchronized void addFiles(boolean append, MediaFile... mediaFiles) {
        addFiles(append, Arrays.asList(mediaFiles));
    }

    /**
     * Removes the music file at the given index.
     *
     * @param index The playlist index.
     */
    public synchronized void removeFileAt(int index) {
        makeBackup();
        index = Math.max(0, Math.min(index, size() - 1));
        if (this.index > index) {
            this.index--;
        }
        files.remove(index);

        this.index = Math.max(0, Math.min(this.index, size() - 1));
    }

    /**
     * Clears the playlist.
     */
    public synchronized void clear() {
        makeBackup();
        files.clear();
        setRandomSearchCriteria(null);
        setInternetRadio(null);
        index = 0;
    }

    /**
     * Shuffles the playlist.
     */
    public synchronized void shuffle() {
        makeBackup();
        MediaFile currentFile = getCurrentFile();
        Collections.shuffle(files);
        if (currentFile != null) {
            Collections.swap(files, files.indexOf(currentFile), 0);
            index = 0;
        }
    }

    /**
     * Sorts the playlist according to the given sort order.
     */
    public synchronized void sort(final SortOrder sortOrder) {
        makeBackup();
        MediaFile currentFile = getCurrentFile();

        Comparator<MediaFile> comparator = (a, b) -> {
            switch (sortOrder) {
                case TRACK:
                    Integer trackA = a.getTrackNumber();
                    Integer trackB = b.getTrackNumber();
                    if (trackA == null) {
                        trackA = 0;
                    }
                    if (trackB == null) {
                        trackB = 0;
                    }
                    return trackA.compareTo(trackB);

                case ARTIST:
                    String artistA = StringUtils.trimToEmpty(a.getArtist());
                    String artistB = StringUtils.trimToEmpty(b.getArtist());
                    return artistA.compareTo(artistB);

                case ALBUM:
                    String albumA = StringUtils.trimToEmpty(a.getAlbumName());
                    String albumB = StringUtils.trimToEmpty(b.getAlbumName());
                    return albumA.compareTo(albumB);
                default:
                    return 0;
            }
        };

        files.sort(comparator);
        if (currentFile != null) {
            index = files.indexOf(currentFile);
        }
    }

    /**
     * Rearranges the playlist using the provided indexes.
     */
    public synchronized void rearrange(List<Integer> indexes) {
        if (indexes == null || indexes.size() != size()) {
            return;
        }
        makeBackup();
        MediaFile[] newFiles = new MediaFile[files.size()];
        IntStream.range(0, size()).parallel().forEach(i -> {
            newFiles[i] = files.get(indexes.get(i));
            if (index == indexes.get(i)) {
                index = i;
            }
        });

        files.clear();
        files.addAll(Arrays.asList(newFiles));
    }

    /**
     * Moves the song at the given index one step up.
     *
     * @param index The playlist index.
     */
    public synchronized void moveUp(int index) {
        moveDown(index - 1);
    }

    /**
     * Moves the song at the given index one step down.
     *
     * @param index The playlist index.
     */
    public synchronized void moveDown(int index) {
        if (index < 0 || index >= size() - 1) {
            return;
        }
        makeBackup();
        Collections.swap(files, index, index + 1);

        if (this.index == index) {
            this.index++;
        } else if (this.index == index + 1) {
            this.index--;
        }
    }

    public RepeatStatus getRepeatStatus() {
        return repeatStatus;
    }

    /**
     * Sets whether the playlist is repeating.
     *
     * @param repeatStatus Whether the playlist is repeating (and how)
     */
    public void setRepeatStatus(RepeatStatus repeatStatus) {
        this.repeatStatus = repeatStatus;
    }

    /**
     * Returns whether the play queue is in shuffle radio mode.
     *
     * @return Whether the play queue is a shuffle radio mode.
     */
    public boolean isShuffleRadioEnabled() {
        return this.randomSearchCriteria != null;
    }

    /**
     * Returns whether the play queue is a internet radio mode.
     *
     * @return Whether the play queue is a internet radio mode.
     */
    public boolean isInternetRadioEnabled() {
        return this.internetRadio != null;
    }

    /**
     * Revert the last operation.
     */
    public synchronized void undo() {
        List<MediaFile> filesTmp = new ArrayList<>(files);
        int indexTmp = index;

        index = indexBackup;
        files = filesBackup;

        indexBackup = indexTmp;
        filesBackup = filesTmp;
    }

    /**
     * Returns the playlist status.
     *
     * @return The playlist status.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Sets the playlist status.
     *
     * @param status The playlist status.
     */
    public synchronized void setStatus(Status status) {
        this.status = status;
        if (index == -1) {
            index = Math.max(0, Math.min(index, size() - 1));
        }
    }

    /**
     * Sets the current internet radio
     *
     * @param internetRadio An internet radio, or <code>null</code> if this is not an internet radio playlist
     */
    public void setInternetRadio(InternetRadio internetRadio) {
        this.internetRadio = internetRadio;
    }

    /**
     * Gets the current internet radio
     *
     * @return The current internet radio, or <code>null</code> if this is not an internet radio playlist
     */
    public InternetRadio getInternetRadio() {
        return internetRadio;
    }

    /**
     * Returns the criteria used to generate this random playlist
     *
     * @return The search criteria, or <code>null</code> if this is not a random playlist.
     */
    public RandomSearchCriteria getRandomSearchCriteria() {
        return randomSearchCriteria;
    }

    /**
     * Sets the criteria used to generate this random playlist
     *
     * @param randomSearchCriteria The search criteria, or <code>null</code> if this is not a random playlist.
     */
    public void setRandomSearchCriteria(RandomSearchCriteria randomSearchCriteria) {
        this.randomSearchCriteria = randomSearchCriteria;
    }

    /**
     * Returns the total length in bytes.
     *
     * @return The total length in bytes.
     */
    public synchronized long length() {
        return files.parallelStream().mapToLong(MediaFile::getFileSize).sum();
    }

    public synchronized double duration() {
        return files.parallelStream().filter(m -> m.getDuration() != null).mapToDouble(MediaFile::getDuration).sum();
    }

    private void makeBackup() {
        filesBackup = new ArrayList<>(files);
        indexBackup = index;
    }

    /**
     * Playlist status.
     */
    public enum Status {
        PLAYING,
        STOPPED
    }

    /**
     * Playlist sort order.
     */
    public enum SortOrder {
        TRACK,
        ARTIST,
        ALBUM
    }

    public enum RepeatStatus {
        OFF, TRACK, QUEUE;

        public static RepeatStatus getNext(RepeatStatus status) {
            switch (status) {
                case OFF: return TRACK;
                case TRACK: return QUEUE;
                case QUEUE: return OFF;
                default: return OFF;
            }
        }
    }
}
