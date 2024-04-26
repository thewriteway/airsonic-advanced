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
import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
@Transactional
public class BookmarkRepositoryTest {

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    MediaFileRepository mediaFileRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @TempDir
    private static Path tempDir;

    @TempDir
    private Path musicFolderDir;

    private final String TEST_USER_NAME = "testUserForBookmark";

    private MediaFile mediaFile;

    private MusicFolder testFolder;

    private List<MediaFile> mediaFileList = new ArrayList<MediaFile>();

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void setup() {
        // clean
        jdbcTemplate.execute("delete from media_file");
        jdbcTemplate.execute("delete from bookmark");

        // music folder
        testFolder = new MusicFolder(musicFolderDir, "name", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);

        // media file
        MediaFile baseFile = new MediaFile();
        baseFile.setFolder(testFolder);
        baseFile.setPath("bookmark.wav");
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
        base2File.setPath("bookmark2.wav");
        base2File.setIndexPath("test2.cue");
        mediaFileRepository.save(base2File);
        mediaFile = mediaFileRepository.findByFolderAndPath(testFolder,"bookmark.wav").get(0);
        mediaFileList.add(mediaFile);
        mediaFileList.add(mediaFileRepository.findByFolderAndPath(testFolder, "bookmark2.wav").get(0));

        // user
        User user = new User(TEST_USER_NAME, "sindre@activeobjects.no", false, 1000L, 2000L, 3000L, Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM, Role.JUKEBOX, Role.SETTINGS));
        userRepository.saveAndFlush(user);
    }

    @AfterEach
    public void clean() {
        userRepository.deleteById(TEST_USER_NAME);
        jdbcTemplate.execute("delete from media_file");
        musicFolderRepository.delete(testFolder);
        jdbcTemplate.execute("delete from bookmark");
    }


    @Test
    public void testSaveBookmark() {
        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(mediaFile.getId());
        bookmark.setPositionMillis(1000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);
        assertNotNull(savedBookmark.getId());
        assertEquals((int)mediaFile.getId(), savedBookmark.getMediaFileId());
        assertEquals(1000L, (long) savedBookmark.getPositionMillis());
        assertEquals(TEST_USER_NAME, savedBookmark.getUsername());
        assertEquals("test comment", savedBookmark.getComment());
    }

    @Test
    public void testFindById() {
        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(mediaFile.getId());
        bookmark.setPositionMillis(2000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findById(savedBookmark.getId());
        assertTrue(optionalBookmark.isPresent());

        Bookmark foundBookmark = optionalBookmark.get();
        assertEquals((int)mediaFile.getId(), foundBookmark.getMediaFileId());
        assertEquals(2000L, (long) foundBookmark.getPositionMillis());
        assertEquals(TEST_USER_NAME, foundBookmark.getUsername());
        assertEquals("test comment", foundBookmark.getComment());
    }

    @Test
    public void testUpdateBookmark() {
        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(mediaFile.getId());
        bookmark.setPositionMillis(3000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);
        savedBookmark.setPositionMillis(4000L);
        bookmarkRepository.save(savedBookmark);

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findById(savedBookmark.getId());
        assertTrue(optionalBookmark.isPresent());

        Bookmark updatedBookmark = optionalBookmark.get();
        assertEquals((int)mediaFile.getId(), updatedBookmark.getMediaFileId());
        assertEquals(4000L, (long) updatedBookmark.getPositionMillis());
        assertEquals(TEST_USER_NAME, updatedBookmark.getUsername());
        assertEquals("test comment", updatedBookmark.getComment());
    }

    @Test
    public void testDeleteBookmark() {
        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(mediaFile.getId());
        bookmark.setPositionMillis(3000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);

        bookmarkRepository.deleteById(savedBookmark.getId());

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findById(savedBookmark.getId());
        assertTrue(optionalBookmark.isEmpty());
    }

    @Test
    public void testFindOptByUsernameAndMediaFileId() {
        int mediaFileId = mediaFile.getId();

        Optional<Bookmark> foundBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(foundBookmark.isEmpty());

        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(mediaFileId);
        bookmark.setPositionMillis(2000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());
        bookmarkRepository.save(bookmark);

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(optionalBookmark.isPresent());

        Optional<Bookmark> foundBookmark2 = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertEquals(mediaFileId, foundBookmark2.get().getMediaFileId());
        assertEquals(2000L, (long) foundBookmark2.get().getPositionMillis());
        assertEquals(TEST_USER_NAME, foundBookmark2.get().getUsername());
        assertEquals("test comment", foundBookmark2.get().getComment());
    }

    @Test
    public void testFindByUsername() {
        List<Bookmark> bookmarks = new ArrayList<>();

        for (int i = 0; i < mediaFileList.size(); i++) {
            Bookmark bookmark = new Bookmark();
            bookmark.setMediaFileId(mediaFileList.get(i).getId());
            bookmark.setPositionMillis(i * 1000L);
            bookmark.setUsername(TEST_USER_NAME);
            bookmark.setComment("test comment " + i);
            bookmark.setCreated(Instant.now());
            bookmark.setChanged(Instant.now());
            bookmarks.add(bookmarkRepository.save(bookmark));
        }

        List<Bookmark> foundBookmarks = bookmarkRepository.findByUsername(TEST_USER_NAME);
        assertEquals(2, foundBookmarks.size());

        for (int i = 0; i < mediaFileList.size(); i++) {
            Bookmark foundBookmark = foundBookmarks.get(i);
            assertEquals((int)mediaFileList.get(i).getId(), foundBookmark.getMediaFileId());
            assertEquals((i) * 1000L, (long) foundBookmark.getPositionMillis());
            assertEquals(TEST_USER_NAME, foundBookmark.getUsername());
            assertEquals("test comment " + (i), foundBookmark.getComment());
        }
    }

    @Test
    public void testDeleteByUsernameAndMediaFileId() {
        int mediaFileId = mediaFile.getId();

        Optional<Bookmark> foundBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(foundBookmark.isEmpty());

        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(mediaFileId);
        bookmark.setPositionMillis(2000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());
        bookmarkRepository.save(bookmark);

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(optionalBookmark.isPresent());

        bookmarkRepository.deleteByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);

        Optional<Bookmark> deletedBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(deletedBookmark.isEmpty());
    }
}
