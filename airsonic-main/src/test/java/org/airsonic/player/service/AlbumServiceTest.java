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

import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.entity.StarredAlbum;
import org.airsonic.player.repository.AlbumRepository;
import org.airsonic.player.repository.OffsetBasedPageRequest;
import org.airsonic.player.repository.StarredAlbumRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AlbumServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private StarredAlbumRepository starredAlbumRepository;

    @InjectMocks
    private AlbumService albumService;

    @Mock
    private MusicFolder mockedMusicFolder;

    @ParameterizedTest
    @CsvSource({
        ",",
        "artist,",
        ",album"
    })
    public void testGetAlbumByMediaFileShouldReturnNull(String artist, String album) {

        // given
        MediaFile mediaFile = new MediaFile();
        mediaFile.setAlbumArtist(artist);
        mediaFile.setAlbumName(album);

        // when
        assertNull(albumService.getAlbumByMediaFile(mediaFile));

        // then
        verifyNoInteractions(albumRepository, starredAlbumRepository);
    }

    @Test
    public void testGetAlbumByMediaFileShouldReturnAlbum() {

        // given
        MediaFile mediaFile = new MediaFile();
        mediaFile.setAlbumArtist("artist");
        mediaFile.setAlbumName("album");
        Album expected = new Album();
        expected.setArtist("artist");
        expected.setName("album");
        expected.setId(1);
        when(albumRepository.findByArtistAndName("artist", "album")).thenReturn(Optional.of(expected));

        // when
        Album actual = albumService.getAlbumByMediaFile(mediaFile);

        // then
        verifyNoInteractions(starredAlbumRepository);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "false, false",
        "true ,false",
        "false, true"
    })
    public void testAlphabeticalAlbums(boolean byArtist, boolean ignoreCase) {

        // given
        List<MusicFolder> musicFolders = new ArrayList<MusicFolder>(List.of(mockedMusicFolder, mockedMusicFolder));
        Album album1 = new Album();
        List<Album> expected = new ArrayList<Album>(List.of(album1));
        when(albumRepository.findByFolderInAndPresentTrue(any(), any(Sort.class))).thenReturn(expected);

        // when
        List<Album> actual = albumService.getAlphabeticalAlbums(byArtist, ignoreCase, musicFolders);

        // then
        assertEquals(expected, actual);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(albumRepository).findByFolderInAndPresentTrue(any(), sortCaptor.capture());
        Sort actualSort = sortCaptor.getValue();
        if (byArtist) {
            assertEquals("artist", actualSort.getOrderFor("artist").getProperty());
            assertEquals(Sort.Direction.ASC, actualSort.getOrderFor("artist").getDirection());
            assertEquals(ignoreCase, actualSort.getOrderFor("artist").isIgnoreCase());
        } else {
            assertNull(actualSort.getOrderFor("artist"));
        }
        assertEquals("name", actualSort.getOrderFor("name").getProperty());
        assertEquals(Sort.Direction.ASC, actualSort.getOrderFor("name").getDirection());
        assertEquals(ignoreCase, actualSort.getOrderFor("name").isIgnoreCase());

        assertEquals("id", actualSort.getOrderFor("id").getProperty());
        assertEquals(Sort.Direction.ASC, actualSort.getOrderFor("id").getDirection());
    }

    @ParameterizedTest
    @CsvSource({
        "1900, 2000",
        "2000, 1900",
        "2000 ,2000"
    })
    public void testGetAlbumsByYear(int startYear, int endYear) {

        Sort.Direction direction = startYear <= endYear ? Sort.Direction.ASC : Sort.Direction.DESC;
        int offset = 100;
        int count = 10;

        // given
        List<MusicFolder> musicFolders = new ArrayList<MusicFolder>(List.of(mockedMusicFolder, mockedMusicFolder));
        Album album1 = new Album();
        List<Album> expected = new ArrayList<Album>(List.of(album1));
        when(albumRepository.findByFolderInAndYearBetweenAndPresentTrue(anyCollection(), anyInt(), anyInt(),
                any(OffsetBasedPageRequest.class))).thenReturn(expected);

        // when
        List<Album> actual = albumService.getAlbumsByYear(offset, count, startYear, endYear, musicFolders);

        // then
        assertEquals(expected, actual);
        ArgumentCaptor<OffsetBasedPageRequest> pageRequestCaptor = ArgumentCaptor
                .forClass(OffsetBasedPageRequest.class);
        verify(albumRepository).findByFolderInAndYearBetweenAndPresentTrue(any(), eq(startYear), eq(endYear),
                pageRequestCaptor.capture());
        OffsetBasedPageRequest actualPageRequest = pageRequestCaptor.getValue();

        assertEquals(offset, actualPageRequest.getOffset());
        assertEquals(count, actualPageRequest.getPageSize());
        assertEquals(direction, actualPageRequest.getSort().getOrderFor("year").getDirection());

    }

    @Test
    public void testGetStarredAlbums() {

        // given
        List<MusicFolder> musicFolders = new ArrayList<MusicFolder>(List.of(mockedMusicFolder, mockedMusicFolder));
        StarredAlbum starredAlbum = new StarredAlbum();
        Album album = new Album();
        album.setId(1);
        starredAlbum.setAlbum(album);
        List<StarredAlbum> starredAlbums = new ArrayList<StarredAlbum>(List.of(starredAlbum));
        List<Album> expected = new ArrayList<Album>(List.of(album));
        when(starredAlbumRepository.findByUsernameAndAlbumFolderInAndAlbumPresentTrue(anyString(), anyCollection(), any(OffsetBasedPageRequest.class))).thenReturn(starredAlbums);
        String username = "username";

        // when
        List<Album> actual = albumService.getStarredAlbums(0, 10, username, musicFolders);

        // then
        assertEquals(expected, actual);
        ArgumentCaptor<OffsetBasedPageRequest> pageRequestCaptor = ArgumentCaptor.forClass(OffsetBasedPageRequest.class);
        verify(starredAlbumRepository).findByUsernameAndAlbumFolderInAndAlbumPresentTrue(eq(username), anyCollection(), pageRequestCaptor.capture());
        OffsetBasedPageRequest actualPageRequest = pageRequestCaptor.getValue();

        assertEquals(0, actualPageRequest.getOffset());
        assertEquals(10, actualPageRequest.getPageSize());
        assertEquals(Sort.Direction.DESC, actualPageRequest.getSort().getOrderFor("created").getDirection());
        assertEquals(Sort.Direction.ASC, actualPageRequest.getSort().getOrderFor("albumId").getDirection());

    }

}
