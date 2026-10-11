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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.controller;

import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * The pages that are displayed in the main pane of the application shell (index.html).
 * <p>
 * The shell loads these pages into its main region without a full page reload, and a browser
 * navigating directly to one of them (deep link, reload, new tab) gets the shell with the page
 * in its main region instead of the bare page.
 */
public final class ShellPages {

    /** Request attribute carrying the main pane URL when a page navigation is forwarded to the shell. */
    public static final String MAIN_ATTRIBUTE = ShellPages.class.getName() + ".main";

    public static final String DEFAULT_MAIN = "nowPlaying.view";

    public static final Set<String> PAGES = Set.of(
            "accessDenied", "advancedSettings", "bookmarks", "changeCoverArt", "createShare",
            "credentialsSettings", "databaseSettings", "dlnaSettings", "editTags", "generalSettings",
            "gettingStarted", "help", "home", "importPlaylist", "internetRadioSettings", "main", "more",
            "musicFolderSettings", "notFound", "nowPlaying", "personalSettings", "playerSettings", "playlist",
            "playlists", "podcastChannel", "podcastChannels", "podcastReceiverAdmin", "podcastSettings",
            "search", "settings", "shareSettings", "sonosSettings", "starred", "status", "transcodingSettings",
            "upload", "userSettings", "videoPlayer");

    private ShellPages() {
    }

    /**
     * @param path a path within the application, with or without leading slash and query string
     * @return whether the path addresses one of the main pane pages
     */
    public static boolean isPage(String path) {
        String name = StringUtils.removeStart(StringUtils.substringBefore(path, '?'), "/");
        return PAGES.contains(StringUtils.removeEnd(name, ".view"));
    }

    /**
     * Validates a requested main pane URL, so the shell never loads anything but one of its own pages.
     *
     * @param requested an application relative URL such as {@code main.view?id=3}, may be null
     * @return the requested URL without leading slash, or {@link #DEFAULT_MAIN} if it is not a main pane page
     */
    public static String toMainUrl(String requested) {
        if (requested == null || !isPage(requested)) {
            return DEFAULT_MAIN;
        }
        return StringUtils.removeStart(requested, "/");
    }
}
