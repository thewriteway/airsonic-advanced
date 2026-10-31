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
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.service.cache.MediaFileCache;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.airsonic.player.util.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MediaFileServiceTest {

    @Mock
    private MetaDataParserFactory metaDataParserFactory;
    @Mock
    private MediaFileRepository mediaFileRepository;
    @Mock
    private CoverArtService coverArtService;
    @Mock
    private MediaFileCache mediaFileCache;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private MediaFileService mediaFileService;


    @Mock
    private MusicFolder mockedFolder;

    @Mock
    private MediaFile mockedMediaFile;

    private final Path CLASS_PATH = Paths.get("src", "test", "resources");

    @BeforeEach
    public void setUp() {
        lenient().when(mockedFolder.getPath()).thenReturn(CLASS_PATH.resolve("MEDIAS"));
    }

    @Test
    public void createIndexedTracksFailedByNoIndexTracksReturnEmptyList() {
        // prepare test data
        MediaFile base = new MediaFile();
        base.setIndexPath("invalidCue/airsonic-test.cue");
        base.setPath("valid/airsonic-test.wav");
        base.setMediaType(MediaType.MUSIC);
        base.setFormat("wav");
        base.setId(10);
        base.setFolder(mockedFolder);

        when(mediaFileRepository.findByFolderAndPath(any(), eq("valid/airsonic-test.wav"))).thenReturn(List.of(mockedMediaFile));
        when(mockedMediaFile.isIndexedTrack()).thenReturn(true);
        when(mediaFileRepository.existsById(any())).thenReturn(true);

        // execute
        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base);

        // check empty list is returned
        assertTrue(actual.isEmpty());
        // verify updateMedia does not called
        verify(mediaFileRepository).findByFolderAndPath(any(), eq("valid/airsonic-test.wav"));
        verify(mediaFileRepository).save(base);
        verify(coverArtService).persistIfNeeded(eq(base));
    }

    @Test
    public void checkLastModifiedRestoresRemountedFile() {
        // a file that was marked non-present (e.g. its mount dropped) and is back with the same mtime
        MediaFile file = new MediaFile();
        file.setId(20);
        file.setPath("piano.mp3");
        file.setFolder(mockedFolder);
        file.setMediaType(MediaType.MUSIC);
        file.setChanged(FileUtil.lastModified(file.getFullPath()));
        file.setPresent(false);

        when(mediaFileRepository.existsById(20)).thenReturn(true);

        MediaFile actual = mediaFileService.checkLastModified(file);

        assertTrue(actual.isPresent());
        verify(mediaFileRepository).save(file);
    }

    @Test
    public void checkLastModifiedDoesNotRewriteMissingFile() {
        MediaFile file = new MediaFile();
        file.setId(21);
        file.setPath("missing.mp3");
        file.setFolder(mockedFolder);
        file.setMediaType(MediaType.MUSIC);
        file.setPresent(false);

        MediaFile actual = mediaFileService.checkLastModified(file);

        assertFalse(actual.isPresent());
        verify(mediaFileRepository, never()).save(any());
    }

    @Test
    public void getSongsForAlbumHidesCueSourceFile() {
        MediaFile source = new MediaFile();
        source.setPath("album/disc.flac");
        source.setIndexPath("album/disc.cue");
        source.setMediaType(MediaType.MUSIC);

        MediaFile track = new MediaFile();
        track.setPath("album/disc.flac");
        track.setStartPosition(0.0);
        track.setMediaType(MediaType.MUSIC);

        when(mediaFileRepository.findByAlbumArtistAndAlbumNameAndMediaTypeInAndPresentTrue(eq("Artist"), eq("Album"), any(), any(Sort.class)))
                .thenReturn(List.of(source, track));
        when(settingsService.getHideVirtualTracks()).thenReturn(false);

        assertEquals(List.of(track), mediaFileService.getSongsForAlbum("Artist", "Album"));

        when(settingsService.getHideVirtualTracks()).thenReturn(true);

        assertEquals(List.of(source), mediaFileService.getSongsForAlbum("Artist", "Album"));
    }
}
