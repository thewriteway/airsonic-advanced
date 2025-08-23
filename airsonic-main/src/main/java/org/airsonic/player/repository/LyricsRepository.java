package org.airsonic.player.repository;

import org.airsonic.player.domain.Lyrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LyricsRepository extends JpaRepository<Lyrics, Integer> {
    // Custom query methods (if needed) can be defined here

    Optional<Lyrics> findById(Integer id);

    Optional<Lyrics> findByMediaFileId(Integer mediaFileId);

}
