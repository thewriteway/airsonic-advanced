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

 Copyright 2023-2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.command.TranscodingCommand;
import org.airsonic.player.command.TranscodingCommand.TranscodingDTO;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Transcoding;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.TranscodingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for the page used to administrate the set of transcoding configurations.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/transcodingSettings", "/transcodingSettings.view"})
public class TranscodingSettingsController {

    @Autowired
    private TranscodingService transcodingService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private AirsonicHomeConfig homeConfig;

    @GetMapping
    public ModelAndView doGet() {

        TranscodingCommand command = new TranscodingCommand();
        command.setTranscodings(transcodingService.getAllTranscodings().stream().map(t -> new TranscodingDTO(t)).toList());
        command.setTranscodeDirectory(homeConfig.getTranscodeDirectory());
        command.setSplitOptions(settingsService.getSplitOptions());
        command.setSplitCommand(settingsService.getSplitCommand());
        command.setDownsampleCommand(settingsService.getDownsamplingCommand());
        command.setHlsCommand(settingsService.getHlsCommand());
        command.setJukeboxCommand(settingsService.getJukeboxCommand());
        command.setVideoImageCommand(settingsService.getVideoImageCommand());
        command.setSubtitlesExtractionCommand(settingsService.getSubtitlesExtractionCommand());
        command.setTranscodeEstimateTimePadding(settingsService.getTranscodeEstimateTimePadding());
        command.setTranscodeEstimateBytePadding(settingsService.getTranscodeEstimateBytePadding());
        command.setBrand(settingsService.getBrand());

        return new ModelAndView("transcodingSettings", "command", command);
    }

    @PostMapping
    public String doPost(@ModelAttribute TranscodingCommand command, RedirectAttributes redirectAttributes) {
        String error = handleParameters(command, redirectAttributes);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
        } else {
            redirectAttributes.addFlashAttribute("settings_toast", true);
        }
        return "redirect:transcodingSettings.view";
    }

    private String handleParameters(TranscodingCommand command, RedirectAttributes redirectAttributes) {

        for (TranscodingDTO transcoding : command.getTranscodings()) {

            if (transcoding.isDelete()) {
                transcodingService.deleteTranscoding(transcoding.getId());
            } else if (transcoding.getName() == null) {
                return "transcodingsettings.noname";
            } else if (transcoding.getSourceFormats() == null) {
                return "transcodingsettings.nosourceformat";
            } else if (transcoding.getTargetFormat() == null) {
                return "transcodingsettings.notargetformat";
            } else if (transcoding.getStep1() == null) {
                return "transcodingsettings.nostep1";
            } else {
                transcodingService.updateTranscoding(
                        transcoding.getId(),
                        transcoding.getName(),
                        transcoding.getSourceFormats(),
                        transcoding.getTargetFormat(),
                        transcoding.getStep1(),
                        transcoding.getStep2(),
                        transcoding.isDefaultActive()
                );
            }
        }

        TranscodingDTO newDto = command.getNewTranscoding();

        Transcoding newTranscoding = new Transcoding(
            null,
            newDto.getName(),
            newDto.getSourceFormats(),
            newDto.getTargetFormat(),
            newDto.getStep1(),
            newDto.getStep2(),
            null,
            newDto.isDefaultActive());
        if (newDto != null && newDto.isConfigured()) {
            String error = null;
            if (newTranscoding.getName() == null) {
                error = "transcodingsettings.noname";
            } else if (newTranscoding.getSourceFormats() == null) {
                error = "transcodingsettings.nosourceformat";
            } else if (newTranscoding.getTargetFormat() == null) {
                error = "transcodingsettings.notargetformat";
            } else if (newTranscoding.getStep1() == null) {
                error = "transcodingsettings.nostep1";
            } else {
                transcodingService.createTranscoding(newTranscoding);
            }
            if (error != null) {
                redirectAttributes.addAttribute("newTranscoding", newTranscoding);
                return error;
            }
        }
        settingsService.setSplitOptions(command.getSplitOptions());
        settingsService.setSplitCommand(command.getSplitCommand());
        settingsService.setDownsamplingCommand(command.getDownsampleCommand());
        settingsService.setHlsCommand(command.getHlsCommand());
        settingsService.setJukeboxCommand(command.getJukeboxCommand());
        settingsService.setVideoImageCommand(command.getVideoImageCommand());
        settingsService.setSubtitlesExtractionCommand(command.getSubtitlesExtractionCommand());
        settingsService.setTranscodeEstimateTimePadding(command.getTranscodeEstimateTimePadding());
        settingsService.setTranscodeEstimateBytePadding(command.getTranscodeEstimateBytePadding());

        settingsService.save();
        return null;
    }

}
