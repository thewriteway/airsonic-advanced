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
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.entity.Share;
import org.airsonic.player.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.*;

/**
 * Controller for sharing music on Twitter, Facebook etc.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/createShare", "createShare.view"})
public class ShareManagementController {

    @Autowired
    private MediaFileService mediaFileService;
    @Autowired
    private ShareService shareService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private PlaylistService playlistService;
    @Autowired
    private SecurityService securityService;

    @GetMapping
    public ModelAndView createShare(HttpServletRequest request, HttpServletResponse response) throws Exception {

        User user = securityService.getCurrentUser(request);
        List<MediaFile> files = getMediaFiles(request, user.getUsername());
        MediaFile dir = null;
        if (!files.isEmpty()) {
            dir = files.get(0);
            if (!dir.isAlbum()) {
                dir = mediaFileService.getParentOf(dir);
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("dir", dir);
        map.put("user", user);

        Share share = shareService.createShare(user.getUsername(), files);
        String description = getDescription(request, user.getUsername());
        if (description != null) {
            share.setDescription(description);
            shareService.updateShare(share);
        }

        map.put("playUrl", shareService.getShareUrl(request, share));

        return new ModelAndView("createShare", "model", map);
    }

    private String getDescription(HttpServletRequest request, String username) throws ServletRequestBindingException {
        Integer playlistId = ServletRequestUtils.getIntParameter(request, "playlist");
        return playlistId == null ? null : playlistService.getReadablePlaylist(playlistId, username).getName();
    }

    /**
     * The files to share. Shares play as the guest user, who can access every music folder, so only files the
     * sharing user may access are shared.
     */
    private List<MediaFile> getMediaFiles(HttpServletRequest request, String username) throws Exception {
        Integer id = ServletRequestUtils.getIntParameter(request, "id");
        Integer playerId = ServletRequestUtils.getIntParameter(request, "player");
        Integer playlistId = ServletRequestUtils.getIntParameter(request, "playlist");

        List<MediaFile> result = new ArrayList<>();

        if (id != null) {
            MediaFile album = mediaFileService.getMediaFile(id);
            if (album == null) {
                throw new IllegalArgumentException("Media file not found: " + id);
            }
            securityService.checkFolderAccess(album, username);
            int[] indexes = ServletRequestUtils.getIntParameters(request, "i");
            if (indexes.length == 0) {
                return Arrays.asList(album);
            }
            List<MediaFile> children = mediaFileService.getVisibleChildrenOf(album, false, true);
            for (int index : indexes) {
                if (index < 0 || index >= children.size()) {
                    throw new IllegalArgumentException("Index out of range: " + index);
                }
                result.add(children.get(index));
            }
            return result;
        } else if (playerId != null) {
            Player player = playerService.getPlayerById(playerId);
            if (player == null || !username.equals(player.getUsername())) {
                throw new AccessDeniedException("Player " + playerId + " does not belong to " + username);
            }
            PlayQueue playQueue = player.getPlayQueue();
            result = playQueue.getFiles();
        } else if (playlistId != null) {
            playlistService.getReadablePlaylist(playlistId, username);
            result = playlistService.getFilesInPlaylist(playlistId);
        }

        return result.stream().filter(f -> securityService.isFolderAccessAllowed(f, username)).toList();
    }

}
