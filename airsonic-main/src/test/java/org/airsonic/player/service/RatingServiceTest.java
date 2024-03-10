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
package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.entity.UserRating;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.UserRatingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RatingServiceTest {

    @Mock
    private MediaFileRepository mediaFileRepository;

    @Mock
    private UserRatingRepository userRatingRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private RatingService ratingService;

    private final ArgumentCaptor<UserRating> userRatingCaptor = ArgumentCaptor.forClass(UserRating.class);

    @Test
    public void testSetRatingForUser() {

        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(1);

        ratingService.setRatingForUser("username", mediaFile, 3);

        verify(userRatingRepository).save(userRatingCaptor.capture());
        verify(userRatingRepository, never()).deleteByUsernameAndMediaFileId(anyString(), anyInt());

        UserRating userRating = userRatingCaptor.getValue();
        assertEquals("username", userRating.getUsername());
        assertEquals(1, userRating.getMediaFileId());
        assertEquals(3, userRating.getRating());
    }

    @Test
    public void testSetRatingForUserNull() {

        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(10);
        doNothing().when(userRatingRepository).deleteByUsernameAndMediaFileId(anyString(), anyInt());

        ratingService.setRatingForUser("username", mediaFile, null);

        verify(userRatingRepository).deleteByUsernameAndMediaFileId(eq("username"), eq(10));
        verify(userRatingRepository, never()).save(any(UserRating.class));
    }

    @ParameterizedTest
    @CsvSource({
        ",",
        "username, ",
        ",1"
    })
    public void testSetRatingForUserNullParameters(String username, Integer mediaFileId) {

        MediaFile mediaFile = Objects.nonNull(mediaFileId) ? new MediaFile() : null;

        ratingService.setRatingForUser(username, mediaFile, 3);

        verify(userRatingRepository, never()).deleteByUsernameAndMediaFileId(anyString(), anyInt());
        verify(userRatingRepository, never()).save(any(UserRating.class));
    }

    @Test
    public void testGetAverageRating() {

        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(1);

        when(userRatingRepository.getAverageRatingByMediaFileId(eq(1))).thenReturn(3.5);

        Double actual = ratingService.getAverageRating(mediaFile);

        verify(userRatingRepository).getAverageRatingByMediaFileId(eq(1));
        assertEquals(3.5, actual);
    }

    @Test
    public void testGetAverageRatingNull() {

        assertNull(ratingService.getAverageRating(null));

        verify(userRatingRepository, never()).getAverageRatingByMediaFileId(anyInt());

    }

    @Test
    public void testGetRatingForUser() {

        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(1);

        when(userRatingRepository.findOptByUsernameAndMediaFileId(eq("username"), eq(1))).thenReturn(Optional.of(new UserRating("username", 1, 3)));

        Integer actual = ratingService.getRatingForUser("username", mediaFile);

        assertEquals(3, actual);
    }

    @ParameterizedTest
    @CsvSource({
        ",",
        "username, ",
        ",1"
    })
    public void testGetRatingForUserNullParameters(String username, Integer mediaFileId) {

        MediaFile mediaFile = Objects.nonNull(mediaFileId) ? new MediaFile() : null;

        Integer actual = ratingService.getRatingForUser(username, mediaFile);

        assertNull(actual);
        verify(userRatingRepository, never()).findOptByUsernameAndMediaFileId(anyString(), anyInt());
    }

}
