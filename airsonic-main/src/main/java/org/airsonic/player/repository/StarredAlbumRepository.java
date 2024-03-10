package org.airsonic.player.repository;

import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.entity.StarredAlbum;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface StarredAlbumRepository extends JpaRepository<StarredAlbum, Integer> {

    List<StarredAlbum> findByUsernameAndAlbumFolderInAndAlbumPresentTrue(String username, Iterable<MusicFolder> musicFolders, Sort sort);

    List<StarredAlbum> findByUsernameAndAlbumFolderInAndAlbumPresentTrue(String username, Iterable<MusicFolder> musicFolders, Pageable pageable);

    Optional<StarredAlbum> findByAlbumAndUsername(Album album, String username);

    @Transactional
    void deleteByAlbumAndUsername(Album album, String username);

}
