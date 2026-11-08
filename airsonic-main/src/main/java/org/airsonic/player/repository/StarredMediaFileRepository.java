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
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.repository;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.entity.StarredMediaFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StarredMediaFileRepository extends JpaRepository<StarredMediaFile, Integer> {

    public int countByUsernameAndMediaFileMediaTypeAndMediaFileFolderInAndMediaFilePresentTrue(
            String username, MediaType mediaType, Iterable<MusicFolder> folders);

    public Optional<StarredMediaFile> findByUsernameAndMediaFile(String username, MediaFile mediaFile);

    public List<StarredMediaFile> findByUsername(String username);

    @Query("SELECT s.mediaFile.id, s.created FROM StarredMediaFile s WHERE s.username = :username AND s.mediaFile.id IN :mediaFileIds")
    public List<Object[]> findMediaFileIdAndCreatedByUsernameAndMediaFileIdIn(@Param("username") String username,
            @Param("mediaFileIds") Collection<Integer> mediaFileIds);

    @Query("SELECT s.mediaFile.id, s.created FROM StarredMediaFile s WHERE s.username = :username")
    public List<Object[]> findMediaFileIdAndCreatedByUsername(@Param("username") String username);

    public List<StarredMediaFile> findByUsernameAndMediaFileMediaTypeAndMediaFileFolderInAndMediaFilePresentTrue(
            String username, MediaType mediaType, Iterable<MusicFolder> folders, Pageable page);

    public List<StarredMediaFile> findByUsernameAndMediaFileMediaTypeInAndMediaFileFolderInAndMediaFilePresentTrue(
            String username, Iterable<MediaType> mediaType, Iterable<MusicFolder> folders, Pageable page);

    @Transactional
    public void deleteAllByMediaFileIdInAndUsername(List<Integer> mediaFileIds, String username);

}
