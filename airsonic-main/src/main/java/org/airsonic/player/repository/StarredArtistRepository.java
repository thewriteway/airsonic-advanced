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

import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.entity.StarredArtist;
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
public interface StarredArtistRepository extends JpaRepository<StarredArtist, Integer> {

    public Optional<StarredArtist> findByArtistIdAndUsername(Integer artistId, String username);

    public List<StarredArtist> findByUsername(String username);

    /**
     * Returns {@code [artistId, created]} pairs for every artist starred by the user, without loading the artists.
     */
    @Query("SELECT s.artist.id, s.created FROM StarredArtist s WHERE s.username = :username")
    public List<Object[]> findArtistIdAndCreatedByUsername(@Param("username") String username);

    @Query("SELECT s.artist.id, s.created FROM StarredArtist s WHERE s.username = :username AND s.artist.id IN :artistIds")
    public List<Object[]> findArtistIdAndCreatedByUsernameAndArtistIdIn(@Param("username") String username,
            @Param("artistIds") Collection<Integer> artistIds);

    public List<StarredArtist> findByUsernameAndArtistFolderInAndArtistPresentTrue(String username, Iterable<MusicFolder> folders, Sort sort);

    @Transactional
    public void deleteByArtistIdAndUsername(Integer artistId, String username);

}
