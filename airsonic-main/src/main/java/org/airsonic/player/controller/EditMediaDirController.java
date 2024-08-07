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
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for updating Media file
 *
 * @author Y. Tory
 */
@Controller
@RequestMapping({"/editMediaDir"})
public class EditMediaDirController {

    @Autowired
    private MediaFileService mediaFileService;

    @PostMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request) throws Exception {
        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");
        String action = request.getParameter("action");

        MediaFile mediaFile = mediaFileService.getMediaFile(id);

        if ("editMediaDirTitle".equals(action) && mediaFile != null && mediaFile.isDirectory()) {
            mediaFile.setTitle(request.getParameter("mediaDirTitle"));
            mediaFileService.updateMediaFile(mediaFile);
        }

        String url = "main.view?id=" + id;
        return new ModelAndView(new RedirectView(url));
    }

}
