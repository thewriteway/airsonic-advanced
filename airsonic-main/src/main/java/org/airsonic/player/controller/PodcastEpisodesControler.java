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

import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.podcast.PodcastDownloadClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the "Podcast episodes"
 *
 * @author Y.Tory
 */
@Controller
@RequestMapping("/podcastEpisodes")
public class PodcastEpisodesControler {

    private static final Logger LOG = LoggerFactory.getLogger(PodcastEpisodesControler.class);

    @Autowired
    private PodcastPersistenceService podcastService;

    @Autowired
    private PodcastDownloadClient podcastDownloadClient;

    @PostMapping(params = "download")
    public String downloadEpisode(
            @RequestParam(name = "episodeId", required = true) Integer episodeId) throws Exception {

        PodcastEpisode episode = podcastService.getEpisode(episodeId, false);
        if (episode == null) {
            LOG.warn("Episode {} not found", episodeId);
            return "redirect:/notFound";
        }

        LOG.info("Downloading episode {}", episodeId);
        Integer channelId = episode.getChannel().getId();
        podcastDownloadClient.downloadEpisode(episodeId);

        return "redirect:/podcastChannel?id=" + channelId;
    }

    /**
     * Initialize episode
     *
     * @param episodeId
     * @return
     * @throws Exception
     */
    @PostMapping(params = "init")
    public String initializeEpisode(
            @RequestParam(name = "episodeId", required = true) Integer episodeId) throws Exception {

        PodcastEpisode episode = podcastService.getEpisode(episodeId, true);
        if (episode == null) {
            LOG.warn("Episode {} not found", episodeId);
            return "redirect:/notFound";
        }

        LOG.info("Initializing episode {}", episodeId);
        Integer channelId = episode.getChannel().getId();
        podcastService.resetEpisode(episodeId);

        return "redirect:/podcastChannel?id=" + channelId;
    }

    /**
     * lock episode
     *
     * @param episodeId
     * @return
     * @throws Exception
     */
    @PostMapping(params = "lock")
    public String lockEpisode(
            @RequestParam(name = "episodeId", required = true) Integer episodeId) throws Exception {

        PodcastEpisode episode = podcastService.getEpisode(episodeId, true);
        if (episode == null) {
            LOG.warn("Episode {} not found", episodeId);
            return "redirect:/notFound";
        }

        LOG.info("Lock episode {}", episodeId);
        Integer channelId = episode.getChannel().getId();
        podcastService.lockEpisode(episodeId);

        return "redirect:/podcastChannel?id=" + channelId;
    }

    /**
     * unlock episode
     *
     * @param episodeId
     * @return
     * @throws Exception
     */
    @PostMapping(params = "unlock")
    public String unlockEpisode(
            @RequestParam(name = "episodeId", required = true) Integer episodeId) throws Exception {

        PodcastEpisode episode = podcastService.getEpisode(episodeId, true);
        if (episode == null) {
            LOG.warn("Episode {} not found", episodeId);
            return "redirect:/notFound";
        }

        LOG.info("Unlock episode {}", episodeId);
        Integer channelId = episode.getChannel().getId();
        podcastService.unlockEpisode(episodeId);

        return "redirect:/podcastChannel?id=" + channelId;
    }

}
