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

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.util.NetworkUtil;
import org.airsonic.player.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.util.List;

/**
 * Controller which produces the M3U playlist.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/play.m3u")
public class M3UController {

    @Autowired
    private PlayerService playerService;
    @Autowired
    private TranscodingService transcodingService;
    @Autowired
    private JWTSecurityService jwtSecurityService;
    @Autowired
    private SecurityService securityService;

    @GetMapping
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("audio/x-mpegurl");
        response.setCharacterEncoding(StringUtil.ENCODING_UTF8);

        String username = securityService.getCurrentUsername(request);

        Player player = playerService.getPlayer(request, response, username);

        String baseUrl = NetworkUtil.getBaseUrl(request);
        String prefix = "ext/stream?";

        if (player.isExternalWithPlaylist()) {
            createClientSidePlaylist(response.getWriter(), player, baseUrl, prefix);
        } else {
            createServerSidePlaylist(response.getWriter(), player, baseUrl, prefix);
        }
        return null;
    }

    private void createClientSidePlaylist(PrintWriter out, Player player, String baseUrl, String prefix) {
        if (player.getM3uBomEnabled()) {
            out.print("\ufeff");
        }
        out.println("#EXTM3U");
        List<MediaFile> result;
        synchronized (player.getPlayQueue()) {
            result = player.getPlayQueue().getFiles();
        }
        for (MediaFile mediaFile : result) {
            Double duration = mediaFile.getDuration();
            if (duration == null) {
                duration = -1.0;
            }
            out.println("#EXTINF:" + duration + "," + mediaFile.getArtist() + " - " + mediaFile.getTitle());

            String urlNoAuth = prefix + "player=" + player.getId() + "&id=" + mediaFile.getId() + "&suffix=." +
                    transcodingService.getSuffix(player, mediaFile, null);
            String urlWithAuth = jwtSecurityService.addJWTToken(player.getUsername(), urlNoAuth);
            out.println(baseUrl + urlWithAuth);
        }
    }

    private void createServerSidePlaylist(PrintWriter out, Player player, String baseUrl, String prefix) {

        String url = prefix + "player=" + player.getId();

        // Get suffix of current file, e.g., ".mp3".
        String suffix = getSuffix(player);
        if (suffix != null) {
            url += "&suffix=." + suffix;
        }

        if (player.getM3uBomEnabled()) {
            out.print("\ufeff");
        }
        out.println("#EXTM3U");
        out.println("#EXTINF:-1,Airsonic");
        out.println(baseUrl + jwtSecurityService.addJWTToken(player.getUsername(), url));
    }

    private String getSuffix(Player player) {
        PlayQueue playQueue = player.getPlayQueue();
        return playQueue.isEmpty() ? null : transcodingService.getSuffix(player, playQueue.getFile(0), null);
    }

}
