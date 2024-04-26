/*
 * This file is part of Airsonic
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Airsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 * Copyright 2023 (C) Y.Tory
 */

package org.airsonic.player.service;

import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.repository.CoverArtRepository;
import org.airsonic.player.service.cache.CoverArtCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CoverArtServiceTest {

    @Mock
    private CoverArtRepository coverArtRepository;

    @Mock
    private CoverArtCache coverArtCache;

    @InjectMocks
    private CoverArtService coverArtService;

    @Mock
    private MusicFolder mockedFolder;

    @Mock
    private MediaFile mockedMediaFile;

    @Mock
    private Album mockedAlbum;

    @Mock
    private Artist mockedArtist;

    @Captor
    private ArgumentCaptor<CoverArt> coverArtCaptor;

    @Captor
    private ArgumentCaptor<List<CoverArt>> coverArtListCaptor;


    @Test
    public void testGetReturnsCachedCoverArt() {
        CoverArt coverArt = new CoverArt(1, EntityType.MEDIA_FILE, "path/to/image.jpg", null, false);
        when(coverArtRepository.findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1)).thenReturn(Optional.of(coverArt));
        CoverArt result = coverArtService.getMediaFileArt(1);
        assertEquals(coverArt, result);
        verify(coverArtRepository, times(1)).findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1);
    }

    @Test
    public void testGetReturnsNullForNonexistentCoverArt() {
        when(coverArtRepository.findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1)).thenReturn(Optional.ofNullable(null));
        CoverArt result = coverArtService.getMediaFileArt(1);
        assertEquals(CoverArt.NULL_ART, result);
        verify(coverArtRepository, times(1)).findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1);
    }

    @ParameterizedTest
    @MethodSource("coverArtWithNullAndNULLART")
    public void testGetFullPathReturnsNullForNullCoverArt(CoverArt coverArt) {
        Path result = coverArtService.getFullPath(coverArt);
        assertNull(result);
    }

    @Test
    public void testGetFullPathReturnsRelativePathIfNoMusicFolderId() {
        CoverArt coverArt = new CoverArt(1, EntityType.MEDIA_FILE, "path/to/image.jpg", null, false);
        Path result = coverArtService.getFullPath(coverArt);
        assertEquals(Paths.get("path/to/image.jpg"), result);
    }

    @Test
    public void testGetFullPathReturnsFullPath() {
        CoverArt coverArt = new CoverArt(1, EntityType.MEDIA_FILE, "path/to/image.jpg", mockedFolder, false);
        when(mockedFolder.getPath()).thenReturn(Paths.get("music"));
        Path result = coverArtService.getFullPath(coverArt);
        assertEquals(Paths.get("music/path/to/image.jpg"), result);
    }

    @Test
    public void testGetFullPathWithTypeAndId() {
        CoverArt coverArt = new CoverArt(1, EntityType.MEDIA_FILE, "path/to/image.jpg", mockedFolder, false);
        when(mockedFolder.getPath()).thenReturn(Paths.get("music"));
        when(coverArtRepository.findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1)).thenReturn(Optional.of(coverArt));
        Path result = coverArtService.getMediaFileArtPath(1);
        assertEquals(Paths.get("music/path/to/image.jpg"), result);
    }

    private static Stream<CoverArt> coverArtWithNullAndNULLART() {
        return Stream.of(null, CoverArt.NULL_ART);
    }

    @Test
    public void testsaveWithCoverArt() {
        CoverArt coverArt = new CoverArt(1, EntityType.MEDIA_FILE, "path/to/image.jpg", mockedFolder, false);
        coverArtService.upsert(coverArt);
        verify(coverArtRepository, times(1)).save(coverArt);
    }

    @Test
    void testExpunge() {

        List<CoverArt> coverArtList = Stream.of(
                new CoverArt(1, EntityType.ALBUM, "path/to/image.jpg", null, false),
                new CoverArt(2, EntityType.ARTIST, "path/to/image.jpg", null, false),
                new CoverArt(3, EntityType.MEDIA_FILE, "path/to/image.jpg", null, false),
                new CoverArt(4, EntityType.ALBUM, "path/to/image.jpg", null, false),
                new CoverArt(5, EntityType.ARTIST, "path/to/image.jpg", null, false),
                new CoverArt(6, EntityType.MEDIA_FILE, "path/to/image.jpg", null, false)
        ).map(art -> {
            if (art.getEntityId() < 4) {
                switch (art.getEntityType()) {
                    case ALBUM:
                        art.setAlbum(new Album());
                        break;
                    case ARTIST:
                        art.setArtist(new Artist());
                        break;
                    case MEDIA_FILE:
                        art.setMediaFile(new MediaFile());
                        break;
                    case NONE:
                        break;
                }
            }
            return art;
        }).collect(Collectors.toList());

        when(coverArtRepository.findAll()).thenReturn(coverArtList);
        coverArtService.expunge();
        verify(coverArtRepository, times(1)).deleteAll(coverArtListCaptor.capture());
        List<CoverArt> deletedCoverArt = coverArtListCaptor.getValue();
        assertEquals(3, deletedCoverArt.size());
        assertTrue(deletedCoverArt.stream().allMatch(art -> art.getEntityId() > 3));
    }

    @Test
    void testDelete() {
        coverArtService.delete(EntityType.MEDIA_FILE, 1);
        verify(coverArtRepository, times(1)).deleteByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1);
    }


    @ParameterizedTest
    @MethodSource("coverArtWithNullAndNULLART")
    void testPersistIfNeededWithMediaFileWithoutCoverArtShouldDoNothing(CoverArt coverArt) {
        when(mockedMediaFile.getArt()).thenReturn(coverArt);
        coverArtService.persistIfNeeded(mockedMediaFile);
        verify(coverArtRepository, never()).save(any());
    }

    @Test
    void testPersistIfNeededWithMediaFileShouldNotOverrideOverriddenCoverArt() {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(1);

        CoverArt existingCoverArt = new CoverArt(1, EntityType.MEDIA_FILE, "path/to/art.jpg", null, true);
        when(coverArtRepository.findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1)).thenReturn(Optional.of(existingCoverArt));

        CoverArt artToPersist = new CoverArt(1, EntityType.MEDIA_FILE, "path/to/updated-art.jpg", null, false);
        mediaFile.setArt(artToPersist);

        coverArtService.persistIfNeeded(mediaFile);

        verify(coverArtRepository).findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1);
        verify(coverArtRepository, never()).save(any(CoverArt.class));
        assertNull(mediaFile.getArt());
    }

    @ParameterizedTest
    @MethodSource("coverArtWillBeOverriddenForMediaFile")
    void testPersistIfNeededWithMediaFileShouldOverrideExistingCoverArt(CoverArt existingCoverArt) {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(1);

        when(coverArtRepository.findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1)).thenReturn(Optional.of(existingCoverArt));

        CoverArt artToPersist = new CoverArt(-1, EntityType.MEDIA_FILE, "path/to/updated-art.jpg", null, false);
        mediaFile.setArt(artToPersist);

        coverArtService.persistIfNeeded(mediaFile);

        verify(coverArtRepository).findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, 1);
        verify(coverArtRepository).save(coverArtCaptor.capture());
        assertNull(mediaFile.getArt());
        CoverArt persistedCoverArt = coverArtCaptor.getValue();
        assertEquals(EntityType.MEDIA_FILE, persistedCoverArt.getEntityType());
        assertEquals(mediaFile.getId(), persistedCoverArt.getEntityId());
        assertEquals("path/to/updated-art.jpg", persistedCoverArt.getPath());
        assertFalse(persistedCoverArt.getOverridden());
    }

    private static Stream<CoverArt> coverArtWillBeOverriddenForMediaFile() {
        return Stream.of(new CoverArt(1, EntityType.MEDIA_FILE, "path/to/art.jpg", null, false), CoverArt.NULL_ART);
    }

    @ParameterizedTest
    @MethodSource("coverArtWithNullAndNULLART")
    void testPersistIfNeededWithAlbumWithoutCoverArtShouldDoNothing(CoverArt coverArt) {
        when(mockedAlbum.getArt()).thenReturn(coverArt);
        coverArtService.persistIfNeeded(mockedAlbum);
        verify(coverArtRepository, never()).save(any());
    }

    @Test
    void testPersistIfNeededWithAlbumShouldNotOverrideOverriddenCoverArt() {
        Album album = new Album();
        album.setId(1);

        CoverArt existingCoverArt = new CoverArt(1, EntityType.ALBUM, "path/to/art.jpg", null, true);
        when(coverArtRepository.findByEntityTypeAndEntityId(EntityType.ALBUM, 1)).thenReturn(Optional.of(existingCoverArt));

        CoverArt artToPersist = new CoverArt(1, EntityType.ALBUM, "path/to/updated-art.jpg", null, false);
        album.setArt(artToPersist);

        coverArtService.persistIfNeeded(album);

        verify(coverArtRepository).findByEntityTypeAndEntityId(EntityType.ALBUM, 1);
        verify(coverArtRepository, never()).save(any(CoverArt.class));
        assertNull(album.getArt());
    }

    @ParameterizedTest
    @MethodSource("coverArtWillBeOverriddenForAlbum")
    void testPersistIfNeededWithAlbumShouldOverrideExistingCoverArt(CoverArt existingCoverArt) {
        Album album = new Album();
        album.setId(1);

        when(coverArtRepository.findByEntityTypeAndEntityId(EntityType.ALBUM, 1)).thenReturn(Optional.of(existingCoverArt));

        CoverArt artToPersist = new CoverArt(-1, EntityType.ALBUM, "path/to/updated-art.jpg", null, false);
        album.setArt(artToPersist);

        coverArtService.persistIfNeeded(album);

        verify(coverArtRepository).findByEntityTypeAndEntityId(EntityType.ALBUM, 1);
        verify(coverArtRepository).save(coverArtCaptor.capture());
        assertNull(album.getArt());
        CoverArt persistedCoverArt = coverArtCaptor.getValue();
        assertEquals(EntityType.ALBUM, persistedCoverArt.getEntityType());
        assertEquals(album.getId(), persistedCoverArt.getEntityId());
        assertEquals("path/to/updated-art.jpg", persistedCoverArt.getPath());
        assertFalse(persistedCoverArt.getOverridden());
    }

    private static Stream<CoverArt> coverArtWillBeOverriddenForAlbum() {
        return Stream.of(new CoverArt(1, EntityType.ALBUM, "path/to/art.jpg", null, false), CoverArt.NULL_ART);
    }

    @ParameterizedTest
    @MethodSource("coverArtWithNullAndNULLART")
    void testPersistIfNeededWithArtistWithoutCoverArtShouldDoNothing(CoverArt coverArt) {
        when(mockedArtist.getArt()).thenReturn(coverArt);
        coverArtService.persistIfNeeded(mockedArtist);
        verify(coverArtRepository, never()).save(any());
    }

    @Test
    void testPersistIfNeededWithArtistShouldNotOverrideOverriddenCoverArt() {
        Artist artist = new Artist();
        artist.setId(1);

        CoverArt existingCoverArt = new CoverArt(1, EntityType.ARTIST, "path/to/art.jpg", null, true);
        when(coverArtRepository.findByEntityTypeAndEntityId(EntityType.ARTIST, 1)).thenReturn(Optional.of(existingCoverArt));

        CoverArt artToPersist = new CoverArt(1, EntityType.ARTIST, "path/to/updated-art.jpg", null, false);
        artist.setArt(artToPersist);

        coverArtService.persistIfNeeded(artist);

        verify(coverArtRepository).findByEntityTypeAndEntityId(EntityType.ARTIST, 1);
        verify(coverArtRepository, never()).save(any(CoverArt.class));
        assertNull(artist.getArt());
    }

    @ParameterizedTest
    @MethodSource("coverArtWillBeOverriddenForArtist")
    void testPersistIfNeededWithArtistShouldOverrideExistingCoverArt(CoverArt existingCoverArt) {
        Artist artist = new Artist();
        artist.setId(1);

        when(coverArtRepository.findByEntityTypeAndEntityId(EntityType.ARTIST, 1)).thenReturn(Optional.of(existingCoverArt));

        CoverArt artToPersist = new CoverArt(-1, EntityType.ARTIST, "path/to/updated-art.jpg", null, false);
        artist.setArt(artToPersist);

        coverArtService.persistIfNeeded(artist);

        verify(coverArtRepository).findByEntityTypeAndEntityId(EntityType.ARTIST, 1);
        verify(coverArtRepository).save(coverArtCaptor.capture());
        assertNull(artist.getArt());
        CoverArt persistedCoverArt = coverArtCaptor.getValue();
        assertEquals(EntityType.ARTIST, persistedCoverArt.getEntityType());
        assertEquals(artist.getId(), persistedCoverArt.getEntityId());
        assertEquals("path/to/updated-art.jpg", persistedCoverArt.getPath());
        assertFalse(persistedCoverArt.getOverridden());
    }

    private static Stream<CoverArt> coverArtWillBeOverriddenForArtist() {
        return Stream.of(new CoverArt(1, EntityType.ARTIST, "path/to/art.jpg", null, false), CoverArt.NULL_ART);
    }

}
