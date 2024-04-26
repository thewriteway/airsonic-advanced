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

import org.airsonic.player.domain.Avatar;
import org.airsonic.player.service.PersonalSettingsService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;

import java.nio.file.Path;

/**
 * Controller which produces avatar images.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/avatar", "/avatar.view"})
public class AvatarController {

    @Autowired
    private ResourceLoader loader;

    @Autowired
    private PersonalSettingsService personalSettingsService;


    /**
    private long getLastModified(Avatar avatar, String username) {
        long result = avatar == null ? -1L : avatar.getCreatedDate().toEpochMilli();

        if (username != null) {
            UserSettings userSettings = settingsService.getUserSettings(username);
            result = Math.max(result, userSettings.getChanged().toEpochMilli());
        }

        return result;
    }
    */

    @GetMapping
    public void handleRequest(
            @RequestParam(name = "id", required = false) Integer id,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "forceCustom", defaultValue = "false") boolean forceCustom,
            HttpServletResponse response) throws Exception {

        if (id == null && username == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Avatar avatar = personalSettingsService.getAvatar(id, username, forceCustom);

        if (avatar == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType(avatar.getMimeType());
        String avatarPath = Path.of("static").resolve(avatar.getPath()).toString();
        Resource res = loader.getResource("classpath:" + avatarPath);
        if (!res.exists()) {
            res = loader.getResource("file:" + avatarPath);
        }
        IOUtils.copy(res.getInputStream(), response.getOutputStream());
    }
}
