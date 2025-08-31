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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.repository;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.PlaylistMediaFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Transactional
@SpringBootTest
class PlaylistMediaFileRepositoryTest {

    @Autowired
    private PlaylistMediaFileRepository playlistMediaFileRepository;

    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setUp() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @TempDir
    private Path tempMusicDir;

    @Test
    public void testGetMediaFilesByRelativePathAndFolderId() {
        // prepare folder
        MusicFolder testFolder = new MusicFolder(tempMusicDir, "name", Type.MEDIA, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);
        // prepare MediaFile
        MediaFile baseFile = new MediaFile();
        baseFile.setFolder(testFolder);
        baseFile.setPath("test.wav");
        baseFile.setMediaType(MediaType.MUSIC);
        baseFile.setIndexPath("test.cue");
        baseFile.setStartPosition(MediaFile.NOT_INDEXED);
        baseFile.setCreated(Instant.now());
        baseFile.setChanged(Instant.now());
        baseFile.setLastScanned(Instant.now());
        baseFile.setChildrenLastUpdated(Instant.now());
        mediaFileRepository.save(baseFile);
        // prepare playlist
        Playlist playlist = new Playlist(
                "admin", false, "Test Playlist", "A test playlist", 1,
                300.0, Instant.now(), Instant.now(), "importedFromTest");
        playlistRepository.save(playlist);

        // relation
        PlaylistMediaFile pmf = new PlaylistMediaFile();
        pmf.setPlaylist(playlist);
        pmf.setMediaFile(baseFile);
        pmf.setOrderIndex(1);
        playlistMediaFileRepository.save(pmf);

        // execute
        List<MediaFile> result = playlistMediaFileRepository.findMediaFilesByPlaylistId(playlist.getId());

        assertEquals(1, result.size());
        assertEquals(baseFile, result.get(0));

    }

    @Test
    @DisplayName("findMediaFilesByPlaylistId returns media files in order for given playlist")
    void testFindMediaFilesByPlaylistId() {

        Playlist playlist = new Playlist(
                "admin", false, "Test Playlist", "A test playlist", 1,
                300.0, Instant.now(), Instant.now(), "importedFromTest");
        playlist = playlistRepository.save(playlist);

        // prepare folder
        MusicFolder testFolder = new MusicFolder(tempMusicDir, "name", Type.MEDIA, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);
        // prepare MediaFile
        MediaFile baseFile = new MediaFile();
        baseFile.setFolder(testFolder);
        baseFile.setPath("test.wav");
        baseFile.setMediaType(MediaType.MUSIC);
        baseFile.setIndexPath("test.cue");
        baseFile.setStartPosition(MediaFile.NOT_INDEXED);
        baseFile.setCreated(Instant.now());
        baseFile.setChanged(Instant.now());
        baseFile.setLastScanned(Instant.now());
        baseFile.setChildrenLastUpdated(Instant.now());
        mediaFileRepository.save(baseFile);

        MediaFile baseFile2 = new MediaFile();
        baseFile2.setFolder(testFolder);
        baseFile2.setPath("test2.wav");
        baseFile2.setMediaType(MediaType.MUSIC);
        baseFile2.setIndexPath("test2.cue");
        baseFile2.setStartPosition(MediaFile.NOT_INDEXED);
        baseFile2.setCreated(Instant.now());
        baseFile2.setChanged(Instant.now());
        baseFile2.setLastScanned(Instant.now());
        baseFile2.setChildrenLastUpdated(Instant.now());
        mediaFileRepository.save(baseFile2);

        PlaylistMediaFile pmf1 = new PlaylistMediaFile();
        pmf1.setPlaylist(playlist);
        pmf1.setMediaFile(baseFile);
        pmf1.setOrderIndex(2);

        PlaylistMediaFile pmf2 = new PlaylistMediaFile();
        pmf2.setPlaylist(playlist);
        pmf2.setMediaFile(baseFile2);
        pmf2.setOrderIndex(1);

        playlistMediaFileRepository.save(pmf1);
        playlistMediaFileRepository.save(pmf2);

        List<MediaFile> result = playlistMediaFileRepository.findMediaFilesByPlaylistId(playlist.getId());

        assertEquals(2, result.size());
        assertEquals(baseFile2, result.get(0));
        assertEquals(baseFile, result.get(1));
    }

    @Test
    @DisplayName("findMediaFilesByPlaylistId returns empty list for non-existent playlist")
    void testFindMediaFilesByPlaylistIdEmpty() {
        List<MediaFile> result = playlistMediaFileRepository.findMediaFilesByPlaylistId(-1);
        assertEquals(0, result.size());
    }
}
