package org.airsonic.player.repository;

import org.airsonic.player.domain.PlaylistMediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaylistMediaFileRepository extends JpaRepository<PlaylistMediaFile, Integer> {
}
