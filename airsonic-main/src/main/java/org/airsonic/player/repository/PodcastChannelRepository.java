package org.airsonic.player.repository;

import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PodcastChannelRepository extends JpaRepository<PodcastChannel, Integer> {

    List<PodcastChannel> findByStatus(PodcastStatus status);

    @Query("SELECT c FROM PodcastChannel c WHERE NOT EXISTS (SELECT 1 FROM PodcastChannelRule r WHERE r.id = c.id)")
    List<PodcastChannel> findWithoutRule();

}
