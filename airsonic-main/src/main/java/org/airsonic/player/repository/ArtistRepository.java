/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.repository;

import org.airsonic.player.domain.Artist;
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

@Repository
public interface ArtistRepository extends JpaRepository<Artist, Integer> {

    Optional<Artist> findByName(String name);

    Optional<Artist> findByNameAndFolderIn(String name, Iterable<MusicFolder> folders);

    List<Artist> findByFolderInAndPresentTrue(Iterable<MusicFolder> folders, Sort sort);

    List<Artist> findByFolderInAndPresentTrue(Iterable<MusicFolder> musicFolders,
                                              Pageable pageable);

    List<Artist> findByPresentFalse();

    boolean existsByName(String name);

    boolean existsByLastScannedBeforeAndPresentTrue(Instant lastScanned);

    @Transactional
    void deleteAllByPresentFalse();

    @Transactional
    @Modifying
    @Query("UPDATE Artist a SET a.present = false WHERE a.lastScanned < :lastScanned")
    void markNonPresent(@Param("lastScanned") Instant lastScanned);

}
