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
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test of {@link MediaFileDao}.
 *
 * @author Y.Tory
 */
@SpringBootTest
@EnableConfigurationProperties(AirsonicHomeConfig.class)
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
public class MediaFileRepositoryTest {

    @Autowired
    MediaFileRepository mediaFileRepository;

    @Autowired
    MusicFolderRepository musicFolderRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @TempDir
    private static Path tempAirsonicDir;

    @TempDir
    private Path tempMusicDir;

    private MusicFolder testFolder;

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
        testFolder = new MusicFolder(tempMusicDir, "name", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);
    }

    @AfterEach
    public void cleanUpAfter() {
        jdbcTemplate.execute("DELETE FROM media_file");
        musicFolderRepository.delete(testFolder);
    }

    @Test
    public void testGetMediaFilesByRelativePathAndFolderId() {
        //prepare
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
        // save
        mediaFileRepository.save(baseFile);

        // assert
        List<MediaFile> registeredTracks = mediaFileRepository.findByFolderAndPath(testFolder, "test.wav");
        assertEquals(1, registeredTracks.size());

        // update
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFolder(testFolder);
        mediaFile.setPath("test.wav");
        mediaFile.setMediaType(MediaType.MUSIC);
        mediaFile.setStartPosition(10.0);
        mediaFile.setCreated(Instant.now());
        mediaFile.setChanged(Instant.now());
        mediaFile.setLastScanned(Instant.now());
        mediaFile.setChildrenLastUpdated(Instant.now());
        mediaFileRepository.save(mediaFile);

        // assertion
        registeredTracks = mediaFileRepository.findByFolderAndPath(testFolder, "test.wav");
        assertEquals(2, registeredTracks.size());
        registeredTracks.forEach(t -> assertEquals("test.wav",t.getPath()));

        MusicFolder wrongFolder = new MusicFolder(tempMusicDir, "wrong", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        wrongFolder.setId(testFolder.getId() + 1);

        List<MediaFile> wrongFolderTracks = mediaFileRepository.findByFolderAndPath(wrongFolder, "test.wav");
        assertEquals(0, wrongFolderTracks.size());

        List<MediaFile> wrongPathTracks = mediaFileRepository.findByFolderAndPath(testFolder, "wrong.wav");
        assertEquals(0, wrongPathTracks.size());
    }

}