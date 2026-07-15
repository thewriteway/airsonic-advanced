package org.airsonic.player.service.playlist;

import chameleon.playlist.SpecificPlaylist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public abstract class PlaylistImportHandler implements Ordered {
    @Autowired
    MediaFileService mediaFileService;

    @Autowired
    SettingsService settingsService;

    abstract public boolean canHandle(Class<? extends SpecificPlaylist> playlistClass);

    abstract public Pair<List<MediaFile>, List<String>> handle(SpecificPlaylist inputSpecificPlaylist, Path location);

    List<MediaFile> getMediaFiles(String pathInPlaylist) {
        if (StringUtils.isNotBlank(pathInPlaylist)) {
            Path path = toPath(pathInPlaylist);
            if (path.isAbsolute()) {
                // there's only one path to look up
                MediaFile m = mediaFileService.getMediaFile(path);
                if (m != null) {
                    return singletonList(m);
                }
            } else {
                // need to resolve the root
                List<MediaFile> possibles = new ArrayList<>();

                // look relative to playlist folder first
                Path playlistFolder = Optional.ofNullable(settingsService.getPlaylistFolder()).map(Paths::get).orElse(null);
                if (playlistFolder != null) {
                    Path resolvedFile = playlistFolder.resolve(path).normalize();
                    MediaFile mediaFile = mediaFileService.getMediaFile(resolvedFile);
                    if (mediaFile != null) {
                        possibles.add(mediaFile);
                    }
                }

                // look relative to all music folders
                possibles.addAll(mediaFileService.getMediaFilesByRelativePath(path).stream()
                        .filter(m -> !EnumSet.of(MediaType.DIRECTORY, MediaType.ALBUM).contains(m.getMediaType()))
                        .collect(toList()));

                // look relative to home
                Path resolvedFile = Paths.get(".").toAbsolutePath().resolve(path).normalize();
                MediaFile mediaFile = mediaFileService.getMediaFile(resolvedFile);
                if (mediaFile != null) {
                    possibles.add(mediaFile);
                }

                return possibles;
            }
        }

        return emptyList();
    }

    /**
     * Playlist entries may be plain paths or file: URIs (e.g. exported by other players).
     * Paths.get() cannot parse file: URIs — on Windows the "C:" colon is even an invalid
     * path character — so convert them explicitly.
     */
    private static Path toPath(String pathInPlaylist) {
        if (StringUtils.startsWithIgnoreCase(pathInPlaylist, "file:")) {
            try {
                return Paths.get(URI.create(pathInPlaylist.replace(" ", "%20")));
            } catch (Exception e) {
                // not a well-formed URI (e.g. "file://C:\dir\song.mp3"); strip the scheme instead
                return Paths.get(pathInPlaylist.replaceFirst("(?i)^file://", ""));
            }
        }
        return Paths.get(pathInPlaylist);
    }
}
