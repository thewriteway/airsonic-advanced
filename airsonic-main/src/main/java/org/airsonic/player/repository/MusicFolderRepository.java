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
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.User;
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
public interface MusicFolderRepository extends JpaRepository<MusicFolder, Integer> {

    public List<MusicFolder> findByDeleted(boolean deleted);

    public List<MusicFolder> findByUsersAndDeletedFalseAndEnabledTrue(User user);

    public Optional<MusicFolder> findByIdAndDeletedFalse(Integer id);

    public Optional<MusicFolder> findByIdAndTypeAndDeletedFalse(Integer id, Type type);

    public List<MusicFolder> findByIdNotAndTypeAndDeletedFalse(Integer id, Type type);

    @Transactional
    public void deleteAllByDeletedTrue();

    /**
     * Updates the path column with the given string as-is. Bypasses the {@link PathConverter}
     * round-trip through {@link java.nio.file.Path}, which would rewrite separators according
     * to the OS this server runs on and corrupt paths meant for another platform.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE music_folder SET path = :path, changed = :changed WHERE id = :id", nativeQuery = true)
    public int updatePathById(@Param("id") Integer id, @Param("path") String path, @Param("changed") Instant changed);

}
