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
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.domain.entity.Share;
import org.airsonic.player.domain.entity.ShareFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
@Transactional
public class ShareRepositoryTest {

    private final String TEST_USER_NAME = "testUserForShare";
    private final String TEST_USER_NAME_2 = "testUserForShare2";

    @TempDir
    private static Path tempDir;

    @TempDir
    private Path musicFolderDir;

    @Autowired
    private ShareRepository shareRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private UserRepository userRepository;

    private MediaFile mediaFile;

    private MusicFolder testFolder;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void init() {
        jdbcTemplate.execute("delete from media_file");
        jdbcTemplate.execute("delete from share");
        jdbcTemplate.execute("delete from share_file");

            // music folder
        testFolder = new MusicFolder(musicFolderDir, "name", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);

        // media file
        MediaFile baseFile = new MediaFile();
        baseFile.setFolder(testFolder);
        baseFile.setPath("userrating.wav");
        baseFile.setMediaType(MediaType.MUSIC);
        baseFile.setIndexPath("test.cue");
        baseFile.setStartPosition(MediaFile.NOT_INDEXED);
        baseFile.setCreated(Instant.now());
        baseFile.setChanged(Instant.now());
        baseFile.setLastScanned(Instant.now());
        baseFile.setChildrenLastUpdated(Instant.now());
        mediaFileRepository.save(baseFile);
        MediaFile base2File = new MediaFile();
        base2File.setFolder(testFolder);
        base2File.setMediaType(MediaType.MUSIC);
        base2File.setStartPosition(MediaFile.NOT_INDEXED);
        base2File.setCreated(Instant.now());
        base2File.setChanged(Instant.now());
        base2File.setLastScanned(Instant.now());
        base2File.setChildrenLastUpdated(Instant.now());
        base2File.setPath("userrating2.wav");
        base2File.setIndexPath("test2.cue");
        mediaFileRepository.save(baseFile);
        mediaFile = mediaFileRepository.findByFolderAndPath(testFolder, "userrating.wav").get(0);

        // user
        User user = new User(TEST_USER_NAME, "rating@activeobjects.no", false, 1000L, 2000L, 3000L, Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM, Role.JUKEBOX, Role.SETTINGS));
        userRepository.saveAndFlush(user);

        User user2 = new User(TEST_USER_NAME_2, "rating2@activeobjects.no", false, 1000L, 2000L, 3000L, Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM, Role.JUKEBOX, Role.SETTINGS));
        userRepository.saveAndFlush(user2);
    }

    @AfterEach
    public void clean() {
        jdbcTemplate.execute("delete from media_file");
        musicFolderRepository.delete(testFolder);
        jdbcTemplate.execute("delete from share");
        jdbcTemplate.execute("delete from share_file");
        userRepository.deleteById(TEST_USER_NAME);
        userRepository.deleteById(TEST_USER_NAME_2);
    }

    @Test
    public void testFindByUsername() {
        Share share = new Share("test", null, TEST_USER_NAME, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), null, 0);
        Share share2 = new Share("test2", null, TEST_USER_NAME, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), null, 0);
        share.setFiles(List.of(new ShareFile(share, mediaFile.getId())));
        shareRepository.save(share);
        shareRepository.save(share2);
        List<Share> actual = shareRepository.findByUsername(TEST_USER_NAME);

        assertEquals(2, actual.size());
        assertTrue(actual.stream().anyMatch(s -> s.getName().equals("test")));
        assertTrue(actual.stream().anyMatch(s -> s.getName().equals("test2")));
        assertTrue(actual.stream().filter(s -> s.getName().equals("test")).findFirst().get().getFiles().stream().anyMatch(f -> f.getMediaFileId().equals(mediaFile.getId())));
    }

    @Test
    public void testFindByName() {
        Share share = new Share("test", null, TEST_USER_NAME, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), null, 0);
        share.setFiles(List.of(new ShareFile(share, mediaFile.getId())));
        shareRepository.save(share);
        Optional<Share> actual = shareRepository.findByName("test");
        assertTrue(actual.isPresent());
        assertEquals("test", actual.get().getName());
        assertTrue(actual.get().getFiles().stream().anyMatch(f -> f.getMediaFileId().equals(mediaFile.getId())));
    }

}
