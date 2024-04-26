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
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.domain.entity.StarredAlbum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnableConfigurationProperties({ AirsonicHomeConfig.class})
@Transactional
public class AlbumRepositoryTest {


    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private StarredAlbumRepository starredAlbumRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    private List<MusicFolder> testFolders = new ArrayList<>();

    @BeforeEach
    public void setupFolder() {
        MusicFolder musicFolder = new MusicFolder(Paths.get("test1"), "musicFolder", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        MusicFolder musicFolder2 = new MusicFolder(Paths.get("test2"), "musicFolder", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        MusicFolder musicFolder3 = new MusicFolder(Paths.get("test3"), "musicFolder", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        testFolders.add(musicFolder2);
        testFolders.add(musicFolder3);
        musicFolderRepository.saveAll(testFolders);
        jdbcTemplate.execute("DELETE FROM album WHERE present = false");
    }

    @AfterEach
    public void clean() {
        musicFolderRepository.deleteAll(testFolders);
        testFolders.clear();
    }

    @Nested
    public class NestedAlbumRepositoryTest {
        @Test
        public void testFindByArtistAndName() {

            // given
            Album album = new Album("path", "name", "artist", Instant.now(), Instant.now(), true, testFolders.get(0));
            albumRepository.save(album);

            // when
            Optional<Album> result = albumRepository.findByArtistAndName("artist", "name");
            Optional<Album> notFound = albumRepository.findByArtistAndName("artist", "notFound");

            // then
            assertTrue(result.isPresent());
            assertEquals(album, result.get());
            assertFalse(notFound.isPresent());
        }

        @Test
        public void testFindByArtistAndFolderIdInAndPresentTrue() {


            // given
            Album album1 = new Album("path1", "name1", "artist1", Instant.now(), Instant.now(), true, testFolders.get(0));
            Album album2 = new Album("path2", "name2", "artist1", Instant.now(), Instant.now(), true, testFolders.get(1));
            Album album3 = new Album("path3", "name3", "artist2", Instant.now(), Instant.now(), true, testFolders.get(1));
            Album album4 = new Album("path3", "name3", "artist3", Instant.now(), Instant.now(), false, testFolders.get(2));

            albumRepository.save(album1);
            albumRepository.save(album2);
            albumRepository.save(album3);
            albumRepository.save(album4);

            // when
            List<Album> result = albumRepository.findByArtistAndFolderInAndPresentTrue("artist1", testFolders);

            // then
            assertEquals(2, result.size());
            assertTrue(result.contains(album1));
            assertTrue(result.contains(album2));

        }

        @Test
        public void testMarkNonPresent() {

            // given
            long count = albumRepository.findByPresentFalse().size();
            Instant now = Instant.now().minusSeconds(3600);
            Album album1 = new Album("path1", "name1", "artist1", Instant.now(), now, true, testFolders.get(0));
            Album album2 = new Album("path2", "name2", "artist1", Instant.now(), now, false, testFolders.get(1));
            Album album3 = new Album("path3", "name3", "artist2", Instant.now(), now.plusSeconds(2), true, testFolders.get(1));
            Album album4 = new Album("path3", "name3", "artist3", Instant.now(), now.plusSeconds(2), false, testFolders.get(2));

            albumRepository.save(album1);
            albumRepository.save(album2);
            albumRepository.save(album3);
            albumRepository.save(album4);
            assertEquals(count + 2, albumRepository.findByPresentFalse().size());

            // when
            albumRepository.markNonPresent(now.plusSeconds(1));

            // then
            List<Album> result = albumRepository.findByPresentFalse();
            assertEquals(count + 3, result.size());
            assertFalse(result.contains(album3));
        }

    }

    @Nested
    public class NestedStarTest {

        @Autowired
        UserRepository userRepository;

        @Autowired
        UserCredentialRepository userCredentialRepository;


        private final String TEST_USER_NAME = "testUserForAlbumRepo";
        private final String TEST_USER_NAME2 = "testUserForAlbumRepo2";

        @BeforeEach
        public void setup() {

            User user = new User(TEST_USER_NAME, "albumrepo@activeobjects.no", false, 1000L, 2000L, 3000L,
                    Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM,
                            Role.JUKEBOX, Role.SETTINGS));
            UserCredential uc = new UserCredential(user, TEST_USER_NAME, "secret", "noop", App.AIRSONIC);
            userRepository.saveAndFlush(user);
            userCredentialRepository.saveAndFlush(uc);
            User user2 = new User(TEST_USER_NAME2, "albumrepo2@activeobjects.no", false, 1000L, 2000L, 3000L,
                    Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM,
                            Role.JUKEBOX, Role.SETTINGS));
            UserCredential uc2 = new UserCredential(user2, TEST_USER_NAME2, "secret", "noop", App.AIRSONIC);
            userRepository.saveAndFlush(user2);
            userCredentialRepository.saveAndFlush(uc2);
        }

        @AfterEach
        public void clean() {
            userRepository.deleteById(TEST_USER_NAME);
            userRepository.deleteById(TEST_USER_NAME2);
        }

        @Test
        public void testFindByUsernameAndAlbumFolderIdInAndAlbumPresentTrue() {

            Album album1 = new Album("path1", "name1", "artist1", Instant.now(), Instant.now(), true, testFolders.get(0));
            Album album2 = new Album("path2", "name2", "artist1", Instant.now(), Instant.now(), false, testFolders.get(1));
            Album album3 = new Album("path3", "name3", "artist2", Instant.now(), Instant.now(), true, testFolders.get(1));
            Album album4 = new Album("path4", "name4", "artist3", Instant.now(), Instant.now(), true, testFolders.get(2));

            albumRepository.saveAndFlush(album1);
            albumRepository.saveAndFlush(album2);
            albumRepository.saveAndFlush(album3);
            albumRepository.saveAndFlush(album4);

            StarredAlbum starredAlbum1 = new StarredAlbum(album1, TEST_USER_NAME, Instant.now());
            StarredAlbum starredAlbum2 = new StarredAlbum(album2, TEST_USER_NAME, Instant.now());
            StarredAlbum starredAlbum3 = new StarredAlbum(album3, TEST_USER_NAME2, Instant.now());
            StarredAlbum starredAlbum4 = new StarredAlbum(album4, TEST_USER_NAME, Instant.now());

            starredAlbumRepository.saveAndFlush(starredAlbum1);
            starredAlbumRepository.saveAndFlush(starredAlbum2);
            starredAlbumRepository.saveAndFlush(starredAlbum3);
            starredAlbumRepository.saveAndFlush(starredAlbum4);

            List<StarredAlbum> result = starredAlbumRepository.findByUsernameAndAlbumFolderInAndAlbumPresentTrue(
                TEST_USER_NAME, testFolders.subList(0, 2), Sort.by(Direction.ASC, "albumId"));

            assertEquals(1, result.size());
            assertEquals(starredAlbum1, result.get(0));
            assertEquals(album1, result.get(0).getAlbum());

        }

        @Test
        public void testFindByIdAndStarredAlbumsUsername() {

            Album album1 = new Album("path1", "name1", "artist1", Instant.now(), Instant.now(), true, testFolders.get(0));
            Album album2 = new Album("path2", "name2", "artist1", Instant.now(), Instant.now(), false, testFolders.get(1));

            albumRepository.saveAndFlush(album1);
            albumRepository.saveAndFlush(album2);

            StarredAlbum starredAlbum1 = new StarredAlbum(album1, TEST_USER_NAME, Instant.now());
            StarredAlbum starredAlbum2 = new StarredAlbum(album2, TEST_USER_NAME2, Instant.now());

            starredAlbumRepository.saveAndFlush(starredAlbum1);
            starredAlbumRepository.saveAndFlush(starredAlbum2);

            Album result = albumRepository.findByIdAndStarredAlbumsUsername(album1.getId(), TEST_USER_NAME).get();
            assertEquals(album1, result);

            Album result2 = albumRepository.findByIdAndStarredAlbumsUsername(album2.getId(), TEST_USER_NAME)
                    .orElse(null);
            assertNull(result2);

            Album result3 = albumRepository.findByIdAndStarredAlbumsUsername(album1.getId(), TEST_USER_NAME2)
                    .orElse(null);
            assertNull(result3);

        }
    }

}
