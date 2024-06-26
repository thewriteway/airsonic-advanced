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
package org.airsonic.player.controller;

import org.airsonic.player.domain.*;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for showing a user's starred items.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/starred", "/starred.view"})
public class StarredController {

    @Autowired
    private PlayerService playerService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private PersonalSettingsService personalSettingsService;
    @Autowired
    private MediaFileService mediaFileService;
    @Autowired
    private MediaFolderService mediaFolderService;

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> map = new HashMap<>();

        User user = securityService.getCurrentUser(request);
        String username = user.getUsername();
        UserSettings userSettings = personalSettingsService.getUserSettings(username);
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        List<MediaFile> artists = mediaFileService.getStarredArtists(0, Integer.MAX_VALUE, username, musicFolders);
        List<MediaFile> albums = mediaFileService.getStarredAlbums(0, Integer.MAX_VALUE, username, musicFolders);
        List<MediaFile> files = mediaFileService.getStarredSongs(0, Integer.MAX_VALUE, username, musicFolders);
        mediaFileService.populateStarredDate(artists, username);
        mediaFileService.populateStarredDate(albums, username);
        mediaFileService.populateStarredDate(files, username);

        List<MediaFile> songs = new ArrayList<>();
        List<MediaFile> videos = new ArrayList<>();
        for (MediaFile file : files) {
            (file.isVideo() ? videos : songs).add(file);
        }

        map.put("user", user);
        map.put("partyModeEnabled", userSettings.getPartyModeEnabled());
        map.put("player", playerService.getPlayer(request, response, username));
        map.put("coverArtSize", CoverArtScheme.MEDIUM.getSize());
        map.put("artists", artists);
        map.put("albums", albums);
        map.put("songs", songs);
        map.put("videos", videos);
        return new ModelAndView("starred","model",map);
    }

}
