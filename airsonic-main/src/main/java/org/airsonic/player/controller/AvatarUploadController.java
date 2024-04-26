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

 Copyright 2023 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import com.google.re2j.Pattern;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller which receives uploaded avatar images.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/avatarUpload", "/avatarUpload.view"})
public class AvatarUploadController {

    private static final Logger LOG = LoggerFactory.getLogger(AvatarUploadController.class);

    @Autowired
    private SecurityService securityService;
    @Autowired
    private PersonalSettingsService personalSettingsService;

    @PostMapping
    protected ModelAndView handleRequestInternal(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {

        String username = securityService.getCurrentUsername(request);
        Map<String, Object> map = new HashMap<String, Object>();

        if (!file.isEmpty()) {
            String filename = file.getOriginalFilename();
            Pattern pattern = Pattern.compile("\\.+/");
            map = personalSettingsService.createCustomAvatar(pattern.matcher(filename).replaceAll(""), file.getBytes(), username);
        } else {
            map.put("error", new Exception("Missing file."));
            LOG.warn("Failed to upload personal image. No file specified.");
        }

        map.put("username", username);
        map.put("avatar", personalSettingsService.getCustomAvatar(username));
        return new ModelAndView("avatarUploadResult", "model", map);
    }

}
