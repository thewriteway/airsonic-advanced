package org.airsonic.player.repository;

import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.entity.CoverArtKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoverArtRepository extends JpaRepository<CoverArt, CoverArtKey> {

    public Optional<CoverArt> findByEntityTypeAndEntityId(EntityType entityType, Integer entityId);

    public List<CoverArt> findByFolder(MusicFolder folder);

    public List<CoverArt> findByFolderAndPathStartsWith(MusicFolder folder, String path);

    @Transactional
    public void deleteByEntityTypeAndEntityId(EntityType entityType, Integer entityId);

}
