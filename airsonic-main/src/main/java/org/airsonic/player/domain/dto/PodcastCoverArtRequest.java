package org.airsonic.player.domain.dto;

import org.airsonic.player.controller.CoverArtController;
import org.airsonic.player.domain.PodcastChannel;

import java.time.Instant;

public class PodcastCoverArtRequest extends CoverArtRequest {

    private final PodcastChannel channel;

    public PodcastCoverArtRequest(PodcastChannel channel) {
        super(null, () -> CoverArtController.PODCAST_COVERART_PREFIX + channel.getId(), () -> Instant.ofEpochMilli(-1));
        this.channel = channel;
    }

    @Override
    public String getAlbum() {
        return null;
    }

    @Override
    public String getArtist() {
        return channel.getTitle() != null ? channel.getTitle() : channel.getUrl();
    }
}