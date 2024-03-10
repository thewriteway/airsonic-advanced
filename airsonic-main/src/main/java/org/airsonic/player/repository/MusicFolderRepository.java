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
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface MusicFolderRepository extends JpaRepository<MusicFolder, Integer> {

    List<MusicFolder> findByDeleted(boolean deleted);

    List<MusicFolder> findByUsersAndDeletedFalseAndEnabledTrue(User user);

    Optional<MusicFolder> findByIdAndDeletedFalse(Integer id);

    Optional<MusicFolder> findByIdAndTypeAndDeletedFalse(Integer id, Type type);

    List<MusicFolder> findByIdNotAndTypeAndDeletedFalse(Integer id, Type type);

    @Transactional
    void deleteAllByDeletedTrue();

}
