package org.airsonic.player.repository;

import org.airsonic.player.domain.MusicFileInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MusicFileInfoRepository extends JpaRepository<MusicFileInfo, Integer> {

    Optional<MusicFileInfo> findByPath(String path);

}
