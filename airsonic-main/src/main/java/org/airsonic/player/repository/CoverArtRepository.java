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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoverArtRepository extends JpaRepository<CoverArt, CoverArtKey> {

    public Optional<CoverArt> findByEntityTypeAndEntityId(EntityType entityType, Integer entityId);

    public List<CoverArt> findByFolder(MusicFolder folder);

    /**
     * Returns the ids of all entities of the given type that have cover art, without loading the cover art.
     */
    @Query("SELECT c.entityId FROM CoverArt c WHERE c.entityType = :entityType")
    public List<Integer> findEntityIdsByEntityType(@Param("entityType") EntityType entityType);

    /**
     * Returns those of the given entity ids that have cover art of the given type, without loading the cover art.
     */
    @Query("SELECT c.entityId FROM CoverArt c WHERE c.entityType = :entityType AND c.entityId IN :entityIds")
    public List<Integer> findEntityIdsByEntityTypeAndEntityIdIn(@Param("entityType") EntityType entityType,
            @Param("entityIds") Collection<Integer> entityIds);

    public List<CoverArt> findByFolderAndPathStartsWith(MusicFolder folder, String path);

    @Transactional
    public void deleteByEntityTypeAndEntityId(EntityType entityType, Integer entityId);

    /**
     * Deletes cover art whose album, artist or media file is missing or no longer present.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM CoverArt c WHERE "
            + "(c.entityType = :album AND NOT EXISTS (SELECT 1 FROM Album a WHERE a.id = c.entityId AND a.present = true)) OR "
            + "(c.entityType = :artist AND NOT EXISTS (SELECT 1 FROM Artist a WHERE a.id = c.entityId AND a.present = true)) OR "
            + "(c.entityType = :mediaFile AND NOT EXISTS (SELECT 1 FROM MediaFile m WHERE m.id = c.entityId AND m.present = true))")
    public int deleteOrphaned(@Param("album") EntityType album, @Param("artist") EntityType artist,
            @Param("mediaFile") EntityType mediaFile);

    /**
     * Rewrites the path separators of all cover art paths in the given folder, e.g. from
     * backslash to slash when a library scanned on Windows is migrated to a Linux host.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE cover_art SET path = REPLACE(path, :oldSeparator, :newSeparator) WHERE folder_id = :folderId", nativeQuery = true)
    public int updatePathSeparatorsByFolderId(@Param("folderId") Integer folderId, @Param("oldSeparator") String oldSeparator, @Param("newSeparator") String newSeparator);

}
