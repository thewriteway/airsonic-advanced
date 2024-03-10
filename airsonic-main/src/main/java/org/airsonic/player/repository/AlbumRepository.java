package org.airsonic.player.repository;

import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MusicFolder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public interface AlbumRepository extends JpaRepository<Album, Integer> {

    Optional<Album> findByArtistAndName(String artist, String name);

    List<Album> findByName(String artist);

    List<Album> findByArtistAndFolderInAndPresentTrue(String artist, Iterable<MusicFolder> musicFolders);

    List<Album> findByArtistAndFolderInAndPresentTrue(String artist, Iterable<MusicFolder> musicFolders, Pageable pageable);

    int countByFolderInAndPresentTrue(Iterable<MusicFolder> musicFolders);

    List<Album> findByFolderInAndPresentTrue(Iterable<MusicFolder> musicFolders, Sort sort);

    List<Album> findByFolderInAndPresentTrue(Iterable<MusicFolder> musicFolders, Pageable pageable);

    List<Album> findByGenreAndFolderInAndPresentTrue(String genre, Iterable<MusicFolder> musicFolders, Pageable pageable);

    List<Album> findByFolderInAndPlayCountGreaterThanAndPresentTrue(Iterable<MusicFolder> musicFolders, AtomicInteger playCount, Pageable pageable);

    List<Album> findByFolderInAndLastPlayedNotNullAndPresentTrue(Iterable<MusicFolder> musicFolders, Pageable pageable);

    List<Album> findByFolderInAndYearBetweenAndPresentTrue(Iterable<MusicFolder> musicFolders, int startYear, int endYear, Pageable pageable);

    List<Album> findByPresentFalse();

    Optional<Album> findByIdAndStarredAlbumsUsername(Integer id, String username);

    boolean existsByLastScannedBeforeAndPresentTrue(Instant lastScanned);

    @Transactional
    void deleteAllByPresentFalse();

    @Transactional
    @Modifying
    @Query("UPDATE Album a SET a.present = false WHERE a.lastScanned < :lastScanned")
    void markNonPresent(@Param("lastScanned") Instant lastScanned);

}