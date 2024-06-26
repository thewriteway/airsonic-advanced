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

 Copyright 2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.command.ChangeCoverArtCommand;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFileService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Controller for changing cover art.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/changeCoverArt", "/changeCoverArt.view"})
public class ChangeCoverArtController {

    @Autowired
    private MediaFileService mediaFileService;

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");
        String artist = request.getParameter("artist");
        String album = request.getParameter("album");
        MediaFile dir = mediaFileService.getMediaFile(id);

        if (StringUtils.isBlank(artist)) {
            artist = dir.getArtist();
        }
        if (StringUtils.isBlank(album)) {
            album = dir.getAlbumName();
        }
        return new ModelAndView("changeCoverArt", "command", new ChangeCoverArtCommand(id, artist, album));

    }

}
