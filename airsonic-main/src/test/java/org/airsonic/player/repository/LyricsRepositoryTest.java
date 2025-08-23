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

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


@EnableConfigurationProperties({ AirsonicHomeConfig.class })
@Transactional
@SpringBootTest
public class LyricsRepositoryTest {

    @Autowired
    private LyricsRepository lyricsRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @TempDir
    private static Path tempAirsonicDir;

    @TempDir
    private Path tempMusicDir;

    private MusicFolder testFolder;

    private MediaFile testMediaFile;

    @BeforeAll
    public static void setUp() {
        System.setProperty("airsonic.home", tempAirsonicDir.toString());
    }

    @AfterAll
    public static void cleanUp() {
        System.clearProperty("airsonic.home");
    }

    @BeforeEach
    public void cleanUpBefore() {
        testFolder = new MusicFolder(tempMusicDir, "name", Type.MEDIA, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);
        // media file
        testMediaFile = new MediaFile();
        testMediaFile.setFolder(testFolder);
        testMediaFile.setPath("bookmark.wav");
        testMediaFile.setMediaType(MediaType.MUSIC);
        testMediaFile.setIndexPath("test.cue");
        testMediaFile.setStartPosition(MediaFile.NOT_INDEXED);
        testMediaFile.setCreated(Instant.now());
        testMediaFile.setChanged(Instant.now());
        testMediaFile.setLastScanned(Instant.now());
        testMediaFile.setChildrenLastUpdated(Instant.now());
        mediaFileRepository.save(testMediaFile);

    }

    @Test
    public void testCreateLyrics() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);

        Lyrics newLyrics = lyricsRepository.findAll().get(0);
        assertEquals(lyrics.getLyrics(), newLyrics.getLyrics());
        assertEquals(lyrics.getCreated(), newLyrics.getCreated());
        assertEquals(lyrics.getUpdated(), newLyrics.getUpdated());
    }

    @Test
    public void testUpdateLyrics() {
        Lyrics lyrics = new Lyrics("Initial lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);
        lyrics = lyricsRepository.findAll().get(0);
        lyrics.setLyrics("Updated lyrics text");
        lyricsRepository.save(lyrics);
        Lyrics updatedLyrics = lyricsRepository.findAll().get(0);
        assertEquals("Updated lyrics text", updatedLyrics.getLyrics());
        assertEquals(lyrics.getCreated(), updatedLyrics.getCreated());
        assertEquals(lyrics.getUpdated(), updatedLyrics.getUpdated());
    }

    @Test
    public void testDeleteLyrics() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);
        assertEquals(1, lyricsRepository.count());
        lyricsRepository.deleteById(lyrics.getId());
        assertEquals(0, lyricsRepository.count());
    }

    @Test
    public void testErrorIfInvalidMediaFileId() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", 9999, "file"); // Assuming 9999 is an invalid ID
        assertThrows(DataIntegrityViolationException.class, () -> lyricsRepository.save(lyrics));
    }

    @Test
    public void testFindByMediaFileId() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);
        Lyrics foundLyrics = lyricsRepository.findByMediaFileId(testMediaFile.getId()).orElse(null);
        assertEquals(lyrics.getLyrics(), foundLyrics.getLyrics());
        assertEquals(lyrics.getMediaFileId(), foundLyrics.getMediaFileId());
        assertNotNull(foundLyrics.getCreated());
        assertNotNull(foundLyrics.getUpdated());
    }

    @Test
    public void testDeleteByCascadeDelete() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);
        assertEquals(1, lyricsRepository.count());

        mediaFileRepository.delete(testMediaFile);
        mediaFileRepository.flush();
        assertEquals(0, lyricsRepository.count(), "Lyrics should be deleted when MediaFile is deleted");
    }

}
