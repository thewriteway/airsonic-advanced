/*
 * This file is part of Airsonic.
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Airsonic is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Copyright 2024 (C) Y.Tory
 *  Copyright 2015 (C) Sindre Mehus
 */

package org.airsonic.player.controller;

import org.airsonic.player.command.PodcastChannelCommand;
import org.airsonic.player.command.PodcastEpisodeCommand;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.podcast.PodcastDownloadClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

import java.util.EnumSet;
import java.util.Set;

/**
 * Controller for the "Podcast channel" page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/podcastChannel", "/podcastChannel.view"})
public class PodcastChannelController {

    @Autowired
    private PodcastPersistenceService podcastService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private PersonalSettingsService personalSettingsService;
    @Autowired
    private PodcastDownloadClient podcastDownloadClient;

    private static final Set<PodcastStatus> DOWNLOADABLE_STATUSES = EnumSet.of(PodcastStatus.SKIPPED, PodcastStatus.ERROR, PodcastStatus.NEW);
    private static final Set<PodcastStatus> LOCKABLE_STATUSES = EnumSet.of(PodcastStatus.SKIPPED, PodcastStatus.COMPLETED, PodcastStatus.NEW);
    private static final Set<PodcastStatus> UNLOCKABLE_STATUSES = EnumSet.of(PodcastStatus.SKIPPED, PodcastStatus.COMPLETED, PodcastStatus.NEW, PodcastStatus.ERROR, PodcastStatus.DELETED);

    @ModelAttribute
    public User getUser(HttpServletRequest request) {
        return securityService.getCurrentUser(request);
    }

    @GetMapping
    protected ModelAndView get(@ModelAttribute User user,
            @RequestParam(name = "id", required = true) Integer channelId,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "10") Integer size) throws Exception {

        PodcastChannelCommand command = new PodcastChannelCommand();
        UserSettings settings = personalSettingsService.getUserSettings(user.getUsername());
        command.setUser(user);
        command.setChannel(podcastService.getChannel(channelId));
        Page<PodcastEpisode> episodes = podcastService.getEpisodes(channelId, PageRequest.of(page, size));
        command.setEpisodesByDAO(episodes.getContent());
        command.setPartyModeEnabled(settings.getPartyModeEnabled());

        ModelAndView result = new ModelAndView();
        result.addObject("command", command);
        result.addObject("pages", episodes);
        return result;
    }

    @PostMapping(params = "delete")
    protected String deleteEpisodes(@ModelAttribute User user,
            @ModelAttribute(name = "channelId") Integer channelId,
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @ModelAttribute("command") PodcastChannelCommand command) throws Exception {

        if (!user.isPodcastRole()) {
            throw new AccessDeniedException("Podcast is forbidden for user " + user.getUsername());
        }

        if (channelId == null) {
            throw new IllegalArgumentException("Channel ID is null");
        }

        command.getEpisodes().stream()
                .filter(ep -> ep.isSelected() && ep.getStatus() != PodcastStatus.DELETED)
                .map(PodcastEpisodeCommand::getId)
                .forEach(id -> podcastService.deleteEpisode(id, true));

        return String.format("redirect:/podcastChannel.view?id=%d&page=%d&size=%d", channelId, page, size);

    }

    @PostMapping(params = "download")
    protected String downloadEpisodes(@ModelAttribute User user,
            @ModelAttribute(name = "channelId") Integer channelId,
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @ModelAttribute("command") PodcastChannelCommand command) throws Exception {

        if (!user.isPodcastRole()) {
            throw new AccessDeniedException("Podcast is forbidden for user " + user.getUsername());
        }

        if (channelId == null) {
            throw new IllegalArgumentException("Channel ID is null");
        }

        command.getEpisodes().parallelStream()
                .filter(ep -> ep.isSelected() && DOWNLOADABLE_STATUSES.contains(ep.getStatus()))
                .map(PodcastEpisodeCommand::getId)
                .forEach(podcastDownloadClient::downloadEpisode);

        return String.format("redirect:/podcastChannel.view?id=%d&page=%d&size=%d", channelId, page, size);

    }

    @PostMapping(params = "lock")
    protected String lockEpisodes(@ModelAttribute User user,
            @ModelAttribute(name = "channelId") Integer channelId,
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @ModelAttribute("command") PodcastChannelCommand command) throws Exception {

        if (!user.isPodcastRole()) {
            throw new AccessDeniedException("Podcast is forbidden for user " + user.getUsername());
        }

        if (channelId == null) {
            throw new IllegalArgumentException("Channel ID is null");
        }

        command.getEpisodes().parallelStream()
                .filter(ep -> ep.isSelected() && LOCKABLE_STATUSES.contains(ep.getStatus()))
                .map(PodcastEpisodeCommand::getId)
                .forEach(podcastService::lockEpisode);

        return String.format("redirect:/podcastChannel.view?id=%d&page=%d&size=%d", channelId, page, size);

    }

    @PostMapping(params = "unlock")
    protected String unlockEpisodes(@ModelAttribute User user,
            @ModelAttribute(name = "channelId") Integer channelId,
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @ModelAttribute("command") PodcastChannelCommand command) throws Exception {

        if (!user.isPodcastRole()) {
            throw new AccessDeniedException("Podcast is forbidden for user " + user.getUsername());
        }

        if (channelId == null) {
            throw new IllegalArgumentException("Channel ID is null");
        }

        command.getEpisodes().parallelStream()
                .filter(ep -> ep.isSelected() && UNLOCKABLE_STATUSES.contains(ep.getStatus()))
                .map(PodcastEpisodeCommand::getId)
                .forEach(podcastService::unlockEpisode);

        return String.format("redirect:/podcastChannel.view?id=%d&page=%d&size=%d", channelId, page, size);

    }

}
