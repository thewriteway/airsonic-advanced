package org.airsonic.player.repository;


import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PodcastEpisodeRepository extends JpaRepository<PodcastEpisode, Integer> {

    Optional<PodcastEpisode> findByChannelAndUrl(PodcastChannel channel, String url);

    Optional<PodcastEpisode> findByChannelAndEpisodeGuid(PodcastChannel channel, String episodeGuid);

    Optional<PodcastEpisode> findByChannelAndTitleAndPublishDate(PodcastChannel channel, String title,
                                                                 Instant publishDate);

    List<PodcastEpisode> findByChannel(PodcastChannel channel);

    List<PodcastEpisode> findByChannelAndStatus(PodcastChannel channel, PodcastStatus status);

    List<PodcastEpisode> findByStatusAndPublishDateNotNullAndMediaFilePresentTrueOrderByPublishDateDesc(
            PodcastStatus status);

}
