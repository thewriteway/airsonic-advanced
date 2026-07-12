package org.airsonic.player.repository;

import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.entity.CoverArtKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Rewrites the path separators of all cover art paths in the given folder, e.g. from
     * backslash to slash when a library scanned on Windows is migrated to a Linux host.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE cover_art SET path = REPLACE(path, :oldSeparator, :newSeparator) WHERE folder_id = :folderId", nativeQuery = true)
    public int updatePathSeparatorsByFolderId(@Param("folderId") Integer folderId, @Param("oldSeparator") String oldSeparator, @Param("newSeparator") String newSeparator);

}
