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

import org.airsonic.player.command.InternetRadioCommand;
import org.airsonic.player.command.InternetRadioCommand.InternetRadioDTO;
import org.airsonic.player.service.InternetRadioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for the page used to administrate the set of internet radio/tv
 * stations.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/internetRadioSettings", "/internetRadioSettings.view"})
public class InternetRadioSettingsController {

    @Autowired
    private InternetRadioService internetRadioService;

    @GetMapping
    public String doGet(Model model) {

        model.addAttribute("command", new InternetRadioCommand(internetRadioService.getAllInternetRadios()));
        return "internetRadioSettings";
    }

    @PostMapping
    public String doPost(@ModelAttribute InternetRadioCommand command, RedirectAttributes redirectAttributes) {

        String error = handleParameters(command);
        if (error == null) {
            redirectAttributes.addFlashAttribute("settings_toast", true);
            redirectAttributes.addFlashAttribute("settings_reload", true);
        }
        redirectAttributes.addFlashAttribute("error", error);
        return "redirect:internetRadioSettings.view";
    }

    private String handleParameters(InternetRadioCommand command) {
        for (InternetRadioDTO radio : command.getInternetRadios()) {

            Integer id = radio.getId();

            if (radio.isDelete()) {
                internetRadioService.deleteInternetRadioById(id);
            } else {
                if (radio.getName() == null) {
                    return "internetradiosettings.noname";
                }
                if (radio.getStreamUrl() == null) {
                    return "internetradiosettings.nourl";
                }
                internetRadioService.updateInternetRadio(id, radio.getName(), radio.getStreamUrl(),
                        radio.getHomepageUrl(), radio.isEnabled());
            }
        }
        InternetRadioDTO newRadio = command.getNewRadio();

        String name = newRadio.getName();
        String streamUrl = newRadio.getStreamUrl();
        String homepageUrl = newRadio.getHomepageUrl();

        if (name != null || streamUrl != null || homepageUrl != null) {
            if (name == null) {
                return "internetradiosettings.noname";
            }
            if (streamUrl == null) {
                return "internetradiosettings.nourl";
            }
            internetRadioService.createInternetRadio(name, streamUrl, homepageUrl, newRadio.isEnabled());
        }

        return null;
    }
}
