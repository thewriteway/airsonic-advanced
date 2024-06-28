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

 Copyright 2024 (C) Y.Tory
 */

package org.airsonic.player.service;

import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.User;
import org.airsonic.player.repository.ArtistRepository;
import org.airsonic.player.repository.StarredArtistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ArtistServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private StarredArtistRepository starredArtistRepository;

    @Mock
    private SettingsService settingsService;

    private JWTSecurityService jwtSecurityService;

    @Mock
    private MediaFileService mediaFileService;

    private ArtistService artistService;

    @Mock
    private MediaFile mockedMediaFile;

    @BeforeEach
    public void setUp() {
        jwtSecurityService = Mockito.spy(new JWTSecurityService(settingsService));
        artistService = new ArtistService(artistRepository, starredArtistRepository, jwtSecurityService,
                mediaFileService);
    }

    @Test
    public void testGetArtistImageURL() {

        // Given
        Artist artist = new Artist();
        artist.setName("artist");
        artist.setId(1);
        when(artistRepository.findByName("artist")).thenReturn(Optional.of(artist));
        when(settingsService.getJWTKey()).thenReturn("jwtkey");

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageURL("http://example.com/", "artist", 30, User.USERNAME_GUEST);
        }

        // Then
        verify(artistRepository).findByName("artist");
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=ar-1&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));

    }

    @Test
    public void testGetArtistImageURLNoArtistShouldReturnNull() {

        // Given
        when(artistRepository.findByName("artist")).thenReturn(Optional.empty());

        // When
        String url = artistService.getArtistImageURL("http://example.com/", "artist", 30, User.USERNAME_GUEST);

        // Then
        verify(artistRepository).findByName("artist");
        verifyNoInteractions(jwtSecurityService, settingsService);
        assertNull(url);
    }

    @Test
    public void testGetArtistImageURLNullArtistNameShouldReturnNull() {

        // When
        String url = artistService.getArtistImageURL("http://example.com/", null, 30, User.USERNAME_GUEST);

        // Then
        verifyNoInteractions(artistRepository, jwtSecurityService, settingsService);
        assertNull(url);
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "true, false",
        "false, true"
    })
    public void testGetArtistImageURLbyMediaFileWithAlbumArtist(boolean isAudio, boolean isAlbum) {

        // Given
        Artist artist = new Artist();
        artist.setName("artist");
        artist.setId(1);
        when(artistRepository.findByName("artist")).thenReturn(Optional.of(artist));
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(isAudio);
        if (!isAudio) {
            when(mockedMediaFile.isAlbum()).thenReturn(isAlbum);
        }
        when(mockedMediaFile.getAlbumArtist()).thenReturn("artist");

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verify(artistRepository).findByName("artist");
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=ar-1&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "true, false",
        "false, true"
    })
    public void testGetArtistImageURLbyMediaFileWithArtist(boolean isAudio, boolean isAlbum) {

        // Given
        Artist artist = new Artist();
        artist.setName("artist");
        artist.setId(1);
        when(artistRepository.findByName("artist")).thenReturn(Optional.of(artist));
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(isAudio);
        if (!isAudio) {
            when(mockedMediaFile.isAlbum()).thenReturn(isAlbum);
        }
        when(mockedMediaFile.getArtist()).thenReturn("artist");

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verify(artistRepository).findByName("artist");
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=ar-1&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
    }

    @Test
    public void testGetArtistImageURLbyMediaFileWithArtistMedia() {

        // Given
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(false);
        when(mockedMediaFile.isAlbum()).thenReturn(false);

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verifyNoInteractions(artistRepository);
        verifyNoMoreInteractions(mockedMediaFile);
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=2&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
    }

    @Test
    public void testGetArtistImageURLbyMediaFileWithAlbumMediaWithNoArtistUrl() {

        // Given
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(false);
        when(mockedMediaFile.isAlbum()).thenReturn(true);
        when(mockedMediaFile.getAlbumArtist()).thenReturn(null);
        when(mockedMediaFile.getArtist()).thenReturn(null);
        when(mediaFileService.getParentOf(mockedMediaFile, true)).thenReturn(mockedMediaFile);

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verifyNoInteractions(artistRepository);
        verifyNoMoreInteractions(mockedMediaFile);
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=2&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
        verify(mediaFileService).getParentOf(mockedMediaFile, true);
    }

    @Test
    public void testGetArtistImageURLbyMediaFileWithAudioMediaWithNoArtistUrl() {

        // Given
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(true);
        when(mockedMediaFile.getAlbumArtist()).thenReturn(null);
        when(mockedMediaFile.getArtist()).thenReturn(null);
        when(mediaFileService.getParentOf(mockedMediaFile, true)).thenReturn(mockedMediaFile);

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verifyNoInteractions(artistRepository);
        verifyNoMoreInteractions(mockedMediaFile);
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=2&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
        verify(mediaFileService, times(2)).getParentOf(mockedMediaFile, true);
    }

}
