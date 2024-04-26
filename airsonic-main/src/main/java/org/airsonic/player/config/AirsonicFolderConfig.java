/*
 * This file is part of Airsonic.
 *
 * Airsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Airsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.config;

import org.airsonic.player.util.Util;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "airsonic")
public class AirsonicFolderConfig {

    // constants
    private static final String DEFAULT_MUSIC_FOLDER_WINDOWS = "c:\\music";
    private static final String DEFAULT_MUSIC_FOLDER_OTHER = "/var/music";
    private static final String DEFAULT_PODCAST_FOLDER_WINDOWS = "c:\\podcast";
    private static final String DEFAULT_PODCAST_FOLDER_OTHER = "/var/podcast";
    private static final String DEFAULT_PLAYLIST_FOLDER_WINDOWS = "c:\\playlists";
    private static final String DEFAULT_PLAYLIST_FOLDER_OTHER = "/var/playlists";

    // airsonic.defaultMusicFolder
    private String defaultMusicFolder = Util.isWindows() ? DEFAULT_MUSIC_FOLDER_WINDOWS : DEFAULT_MUSIC_FOLDER_OTHER;
    // airsonic.defaultPodcastFolder
    private String defaultPodcastFolder = Util.isWindows() ? DEFAULT_PODCAST_FOLDER_WINDOWS : DEFAULT_PODCAST_FOLDER_OTHER;
    // airsonic.defaultPlaylistFolder
    private String defaultPlaylistFolder = Util.isWindows() ? DEFAULT_PLAYLIST_FOLDER_WINDOWS : DEFAULT_PLAYLIST_FOLDER_OTHER;
    // airsonic.hideVirtualTracks
    private boolean hideVirtualTracks = true;



    /**
     * Returns the directory
     *
     * @param loadedProperty The property loaded from the configuration file.
     * @param windowsPath The default path to use if running on Windows.
     * @param otherPath The default path to use if running on other platforms.
     * @return The directory. Never {@code null}.
     */
    private String getFolderProperty(String loadedProperty, String windowsPath, String otherPath) {
        if (StringUtils.hasText(loadedProperty)) {
            return loadedProperty;
        }
        return Util.isWindows() ? windowsPath : otherPath;
    }

    public String getDefaultMusicFolder() {
        return defaultMusicFolder;
    }

    public String getDefaultPodcastFolder() {
        return defaultPodcastFolder;
    }

    public String getDefaultPlaylistFolder() {
        return defaultPlaylistFolder;
    }

    public boolean isHideVirtualTracks() {
        return hideVirtualTracks;
    }

    public void setDefaultMusicFolder(String defaultMusicFolder) {
        this.defaultMusicFolder = getFolderProperty(defaultMusicFolder, DEFAULT_MUSIC_FOLDER_WINDOWS, DEFAULT_MUSIC_FOLDER_OTHER);
    }

    public void setDefaultPodcastFolder(String defaultPodcastFolder) {
        this.defaultPodcastFolder = getFolderProperty(defaultPodcastFolder, DEFAULT_PODCAST_FOLDER_WINDOWS, DEFAULT_PODCAST_FOLDER_OTHER);
    }

    public void setDefaultPlaylistFolder(String defaultPlaylistFolder) {
        this.defaultPlaylistFolder = getFolderProperty(defaultPlaylistFolder, DEFAULT_PLAYLIST_FOLDER_WINDOWS, DEFAULT_PLAYLIST_FOLDER_OTHER);
    }

    public void setHideVirtualTracks(boolean hideVirtualTracks) {
        this.hideVirtualTracks = hideVirtualTracks;
    }
}
