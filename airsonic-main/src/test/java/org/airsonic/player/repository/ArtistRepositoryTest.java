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

import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.domain.entity.StarredArtist;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnableConfigurationProperties
@Transactional
public class ArtistRepositoryTest {

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private StarredArtistRepository starredArtistRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private static Path tempDir;

    private List<MusicFolder> testFolders = new ArrayList<>();

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void init() {
        MusicFolder musicFolder = new MusicFolder(Paths.get("test1"), "musicFolder", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        MusicFolder musicFolder2 = new MusicFolder(Paths.get("test2"), "musicFolder2", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        testFolders.add(musicFolder2);
        musicFolderRepository.saveAll(testFolders);
        jdbcTemplate.execute("DELETE FROM artist WHERE present = false");
    }

    @AfterEach
    public void cleanup() {
        musicFolderRepository.deleteAll(testFolders);
        testFolders.clear();
    }

    @Nested
    public class ArtistTest {

        @Test
        public void testFindByName() {
            assertTrue(artistRepository.findByName("name").isEmpty());

            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now());

            artistRepository.save(artist);
            assertTrue(artistRepository.findByName("name").isPresent());
            artistRepository.delete(artist);
        }

        @Test
        public void testFindByNameAndFolderIdIn() {
            assertTrue(artistRepository.findByNameAndFolderIn("name", testFolders.subList(0, 1)).isEmpty());

            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now());
            artist.setFolder(testFolders.get(0));

            artistRepository.save(artist);
            assertTrue(artistRepository.findByNameAndFolderIn("name", testFolders.subList(0, 1)).isPresent());

            artistRepository.delete(artist);
        }

        @Test
        public void testFindByFolderIdInAndPresentTrue() {
            assertTrue(artistRepository.findByFolderInAndPresentTrue(testFolders.subList(0,2), Sort.by(Direction.ASC, "name")).isEmpty());

            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now());
            artist.setFolder(testFolders.get(0));
            artist.setPresent(true);
            artistRepository.save(artist);

            Artist artist2 = new Artist("name2");
            artist2.setLastScanned(Instant.now());
            artist2.setFolder(testFolders.get(1));
            artist2.setPresent(true);
            artistRepository.save(artist2);
            assertTrue(artistRepository.findByFolderInAndPresentTrue(testFolders.subList(0,2), Sort.by(Direction.ASC, "name")).size() == 2);

            artistRepository.delete(artist);
            artistRepository.delete(artist2);
        }

        @Test
        public void testFindByPresentFalse() {

            long count = artistRepository.findByPresentFalse().size();
            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now());
            artist.setPresent(false);
            artistRepository.save(artist);
            assertEquals(count + 1, artistRepository.findByPresentFalse().size());

            artistRepository.delete(artist);
        }

        @Test
        public void testMarkNonPresent() {
            long count = artistRepository.findByPresentFalse().size();
            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now().minusSeconds(86400));
            artist.setPresent(true);
            artistRepository.saveAndFlush(artist);
            assertTrue(artistRepository.findByName("name").isPresent());

            artistRepository.markNonPresent(Instant.now().minusSeconds(86400));
            assertEquals(count + 1, artistRepository.findByPresentFalse().size());
            artistRepository.delete(artist);
        }

        @Test
        public void testDeleteAllByPresentFalse() {
            long count = artistRepository.findByPresentFalse().size();

            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now());
            artist.setPresent(false);
            artistRepository.save(artist);
            assertEquals(count + 1, artistRepository.findByPresentFalse().size());

            artistRepository.deleteAllByPresentFalse();
            assertTrue(artistRepository.findByPresentFalse().isEmpty());
        }
    }

    @Nested
    class StarredArtistTest {

        @Autowired UserRepository userRepository;

        @Autowired UserCredentialRepository userCredentialRepository;

        private final String TEST_USER_NAME = "testUserForArtistRepo";

        @BeforeEach
        public void setup() {

            User user = new User(TEST_USER_NAME, "artistrepo@activeobjects.no", false, 1000L, 2000L, 3000L, Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM, Role.JUKEBOX, Role.SETTINGS));
            UserCredential uc = new UserCredential(user, TEST_USER_NAME, "secret", "noop", App.AIRSONIC);
            userRepository.saveAndFlush(user);
            userCredentialRepository.saveAndFlush(uc);
        }

        @AfterEach
        public void clean() {
            userRepository.deleteById(TEST_USER_NAME);
        }

        @Test
        public void testFindByUsername() {

            // given
            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now());
            artistRepository.save(artist);
            Artist artist2 = new Artist("name2");
            artist2.setLastScanned(Instant.now());
            artistRepository.save(artist2);

            StarredArtist starredArtist = new StarredArtist(artist, TEST_USER_NAME, Instant.now());
            starredArtistRepository.save(starredArtist);
            StarredArtist starredArtist2 = new StarredArtist(artist2, TEST_USER_NAME, Instant.now());
            starredArtistRepository.save(starredArtist2);

            // when / then
            assertEquals(2, starredArtistRepository.findByUsername(TEST_USER_NAME).size());

            // cleanup
            artistRepository.delete(artist);
            artistRepository.delete(artist2);
        }

        @Test
        public void testFindByArtistAndUsername() {

            // given
            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now());
            artistRepository.save(artist);
            StarredArtist starredArtist = new StarredArtist(artist, TEST_USER_NAME, Instant.now());
            starredArtistRepository.save(starredArtist);

            // when / then
            assertTrue(starredArtistRepository.findByArtistIdAndUsername(artist.getId(), TEST_USER_NAME).isPresent());
            assertFalse(starredArtistRepository.findByArtistIdAndUsername(artist.getId(), "not" + TEST_USER_NAME).isPresent());
            assertFalse(starredArtistRepository.findByArtistIdAndUsername(artist.getId() + 1, TEST_USER_NAME).isPresent());

            // cleanup
            artistRepository.delete(artist);
        }

        @Test
        public void testFindByUsernameAndArtistFolderIdInAndArtistPresentTrue() {

            // given
            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now());
            artist.setFolder(testFolders.get(0));
            artist.setPresent(true);
            artistRepository.save(artist);
            Artist artist2 = new Artist("name2");
            artist2.setLastScanned(Instant.now());
            artist2.setFolder(testFolders.get(1));
            artist2.setPresent(true);
            artistRepository.save(artist2);
            // not present
            Artist artist3 = new Artist("name3");
            artist3.setLastScanned(Instant.now());
            artist3.setFolder(testFolders.get(1));
            artist3.setPresent(false);
            artistRepository.save(artist3);

            StarredArtist starredArtist = new StarredArtist(artist, TEST_USER_NAME, Instant.now());
            starredArtistRepository.save(starredArtist);
            StarredArtist starredArtist2 = new StarredArtist(artist2, TEST_USER_NAME, Instant.now());
            starredArtistRepository.save(starredArtist2);
            StarredArtist starredArtist3 = new StarredArtist(artist3, TEST_USER_NAME, Instant.now());
            starredArtistRepository.save(starredArtist3);

            // when
            List<StarredArtist> starredArtists = starredArtistRepository
                    .findByUsernameAndArtistFolderInAndArtistPresentTrue(TEST_USER_NAME,
                            testFolders.subList(0, 2), Sort.by("created"));

            // then
            assertEquals(2, starredArtists.size());
            assertEquals(artist, starredArtists.get(0).getArtist());
            assertEquals(artist2, starredArtists.get(1).getArtist());

            // cleanup
            artistRepository.delete(artist);
            artistRepository.delete(artist2);
            artistRepository.delete(artist3);
        }

        @Test
        public void testDeleteByArtistIdAndUsername() {

            // given
            Artist artist = new Artist("name");
            artist.setLastScanned(Instant.now());
            artistRepository.save(artist);
            StarredArtist starredArtist = new StarredArtist(artist, TEST_USER_NAME, Instant.now());
            starredArtistRepository.save(starredArtist);

            // when
            starredArtistRepository.deleteByArtistIdAndUsername(artist.getId(), TEST_USER_NAME);

            // then
            assertTrue(starredArtistRepository.findByArtistIdAndUsername(artist.getId(), TEST_USER_NAME).isEmpty());

            // cleanup
            artistRepository.delete(artist);
        }
    }
}
