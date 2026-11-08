package org.airsonic.player.repository;

import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.entity.StarredAlbum;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StarredAlbumRepository extends JpaRepository<StarredAlbum, Integer> {

    public List<StarredAlbum> findByUsernameAndAlbumFolderInAndAlbumPresentTrue(String username, Iterable<MusicFolder> musicFolders, Sort sort);

    public List<StarredAlbum> findByUsernameAndAlbumFolderInAndAlbumPresentTrue(String username, Iterable<MusicFolder> musicFolders, Pageable pageable);

    public Optional<StarredAlbum> findByAlbumAndUsername(Album album, String username);

    public Optional<StarredAlbum> findByAlbumIdAndUsername(Integer albumId, String username);

    /**
     * Returns the album ids and starred dates of the given albums that the user has starred.
     */
    @Query("SELECT s.album.id, s.created FROM StarredAlbum s WHERE s.username = :username AND s.album.id IN :albumIds")
    public List<Object[]> findAlbumIdAndCreatedByUsernameAndAlbumIdIn(@Param("username") String username,
            @Param("albumIds") Collection<Integer> albumIds);

    @Transactional
    public void deleteByAlbumAndUsername(Album album, String username);

}
