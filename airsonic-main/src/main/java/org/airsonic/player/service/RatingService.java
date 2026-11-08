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
package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.entity.UserRating;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.OffsetBasedPageRequest;
import org.airsonic.player.repository.UserRatingRepository;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides services for user ratings.
 *
 * @author Sindre Mehus
 */
@Service
public class RatingService {

    private static final Logger LOG = LoggerFactory.getLogger(RatingService.class);

    @Autowired
    private SecurityService securityService;
    @Autowired
    private UserRatingRepository userRatingRepository;
    @Autowired
    private MediaFileRepository mediaFileRepository;

    /**
     * Returns the highest rated albums.
     *
     * @param offset      Number of albums to skip.
     * @param count       Maximum number of albums to return.
     * @param musicFolders Only return albums in these folders.
     * @return The highest rated albums.
     */
    @Transactional(readOnly = true)
    public List<MediaFile> getHighestRatedAlbums(int offset, int count, List<MusicFolder> musicFolders) {

        if (count < 1 || musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        // the database averages, orders and pages; only the page's albums are loaded
        List<Object[]> averages = userRatingRepository.findAverageRatings(musicFolders, MediaFile.MediaType.ALBUM,
                new OffsetBasedPageRequest(offset, count));
        if (averages.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, MediaFile> albumsById = mediaFileRepository
                .findAllById(averages.stream().map(row -> (Integer) row[0]).toList()).stream()
                .collect(Collectors.toMap(MediaFile::getId, Function.identity()));

        List<MediaFile> result = new ArrayList<>(averages.size());
        for (Object[] row : averages) {
            MediaFile album = albumsById.get(row[0]);
            if (album != null && securityService.isReadAllowed(album, true)) {
                album.setAverageRating(((Number) row[1]).doubleValue());
                result.add(album);
            }
        }
        return result;
    }

    /**
     * Sets the rating for a music file and a given user.
     *
     * @param username  The user name.
     * @param mediaFile The music file.
     * @param rating    The rating between 1 and 5, or <code>null</code> to remove the rating.
     */
    @Transactional
    public void setRatingForUser(String username, MediaFile mediaFile, Integer rating) {
        if (username == null || mediaFile == null) {
            return;
        }
        if (rating == null) {
            userRatingRepository.deleteByUsernameAndMediaFileId(username, mediaFile.getId());
        } else {
            UserRating userRating = new UserRating(username, mediaFile.getId(), rating);
            try {
                userRatingRepository.save(userRating);
            } catch (Exception e) {
                LOG.error("Failed to save rating for user {} and media file {}", username, mediaFile.getId(), e);
            }
        }
    }

    /**
     * Returns the average rating for the given music file.
     *
     * @param mediaFile The music file.
     * @return The average rating, or <code>null</code> if no ratings are set.
     */
    @Transactional
    public Double getAverageRating(MediaFile mediaFile) {
        if (mediaFile == null) {
            return null;
        }
        return userRatingRepository.getAverageRatingByMediaFileId(mediaFile.getId());
    }

    /**
     * Returns the rating for the given user and music file.
     *
     * @param username  The user name.
     * @param mediaFile The music file.
     * @return The rating, or <code>null</code> if no rating is set.
     */
    @Transactional
    public Integer getRatingForUser(String username, MediaFile mediaFile) {
        if (username == null || mediaFile == null) {
            return null;
        }
        return userRatingRepository.findOptByUsernameAndMediaFileId(username, mediaFile.getId()).map(UserRating::getRating).orElse(null);
    }

    /**
     * Returns the user's ratings for those of the given media files the user rated, keyed by media file id, in one
     * query per 500 ids. Use this instead of one {@link #getRatingForUser} call per file for long lists.
     */
    @Transactional(readOnly = true)
    public Map<Integer, Integer> getRatingsForUser(String username, Collection<Integer> mediaFileIds) {
        Map<Integer, Integer> result = new HashMap<>();
        if (username == null) {
            return result;
        }
        for (List<Integer> chunk : partition(mediaFileIds)) {
            for (Object[] row : userRatingRepository.findMediaFileIdAndRatingByUsernameAndMediaFileIdIn(username, chunk)) {
                result.put((Integer) row[0], (Integer) row[1]);
            }
        }
        return result;
    }

    /**
     * Returns all of the user's ratings, keyed by media file id, in one query. Suits lists that span the whole
     * library, where looking the ids up in chunks would take many queries.
     */
    @Transactional(readOnly = true)
    public Map<Integer, Integer> getRatingsForUser(String username) {
        Map<Integer, Integer> result = new HashMap<>();
        if (username == null) {
            return result;
        }
        for (Object[] row : userRatingRepository.findMediaFileIdAndRatingByUsername(username)) {
            result.put((Integer) row[0], (Integer) row[1]);
        }
        return result;
    }

    /**
     * Returns the average ratings of those of the given media files that have ratings, keyed by media file id, in one
     * query per 500 ids. Use this instead of one {@link #getAverageRating} call per file for long lists.
     */
    @Transactional(readOnly = true)
    public Map<Integer, Double> getAverageRatings(Collection<Integer> mediaFileIds) {
        Map<Integer, Double> result = new HashMap<>();
        for (List<Integer> chunk : partition(mediaFileIds)) {
            for (Object[] row : userRatingRepository.findAverageRatingsByMediaFileIdIn(chunk)) {
                result.put((Integer) row[0], ((Number) row[1]).doubleValue());
            }
        }
        return result;
    }

    /**
     * Returns the average rating of every rated media file, keyed by media file id, in one query. Suits lists that
     * span the whole library, where looking the ids up in chunks would take many queries.
     */
    @Transactional(readOnly = true)
    public Map<Integer, Double> getAverageRatings() {
        Map<Integer, Double> result = new HashMap<>();
        for (Object[] row : userRatingRepository.findAllAverageRatings()) {
            result.put((Integer) row[0], ((Number) row[1]).doubleValue());
        }
        return result;
    }

    private static List<List<Integer>> partition(Collection<Integer> ids) {
        return ListUtils.partition(new ArrayList<>(new LinkedHashSet<>(ids)), 500);
    }

    /**
     * Returns the number of albums rated by the given user.
     *
     * @param username The user name.
     * @param musicFolders Only return albums in these folders.
     * @return The number of albums rated by the given user.
     */
    public int getRatedAlbumCount(String username, List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return 0;
        }
        return userRatingRepository.countRatedByUsername(username, musicFolders, MediaFile.MediaType.ALBUM);
    }

}
