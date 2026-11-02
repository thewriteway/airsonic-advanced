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

import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.repository.BookmarkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookmarkServiceTest {

    @InjectMocks
    private BookmarkService bookmarkService;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private MediaFileService mediaFileService;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private Bookmark bookmark;

    @BeforeEach
    public void setUp() throws Exception {
        bookmark = new Bookmark(1, 2, 5000L, "testUser", "test comment", null, null);
    }

    @Test
    public void testGetBookmark() {
        when(bookmarkRepository.findOptByUsernameAndMediaFileId("testUser", 2)).thenReturn(Optional.of(bookmark));
        Optional<Bookmark> retrievedBookmark = bookmarkService.getBookmark("testUser", 2);
        assertTrue(retrievedBookmark.isPresent());
        assertEquals(retrievedBookmark.get().getId(), bookmark.getId());
    }

    @Test
    public void testSetBookmark() {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setDuration((double) 10000);
        when(mediaFileService.getMediaFile(2)).thenReturn(mediaFile);
        when(bookmarkRepository.findOptByUsernameAndMediaFileId("testUser", 2)).thenReturn(Optional.empty());
        when(bookmarkRepository.save(any(Bookmark.class))).thenReturn(bookmark);

        boolean result = bookmarkService.setBookmark("testUser", 2, 5000L, "test comment");
        assertTrue(result);
    }

    @Test
    public void testCreateBookmarkSavesPositionsSetBookmarkSkips() {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setDuration((double) 100);
        when(mediaFileService.getMediaFile(2)).thenReturn(mediaFile);
        when(bookmarkRepository.findOptByUsernameAndMediaFileId("testUser", 2)).thenReturn(Optional.empty());
        when(bookmarkRepository.save(any(Bookmark.class))).thenReturn(bookmark);

        // near the start and near the end, which setBookmark ignores or deletes
        assertTrue(bookmarkService.createBookmark("testUser", 2, 0L, null));
        assertTrue(bookmarkService.createBookmark("testUser", 2, 95000L, null));

        verify(bookmarkRepository, times(2)).save(any(Bookmark.class));
        verify(bookmarkRepository, never()).deleteByUsernameAndMediaFileId(anyString(), anyInt());
    }

    @Test
    public void testCreateBookmarkReturnsFalseIfMediaFileIsNull() {
        when(mediaFileService.getMediaFile(2)).thenReturn(null);

        assertFalse(bookmarkService.createBookmark("testUser", 2, 0L, null));
        verify(bookmarkRepository, never()).save(any(Bookmark.class));
    }

    @Test
    public void testSetBookmarkReturnsFalseIfMediaFileIsNull() {
        // Arrange
        int mediaFileId = 123;
        String username = "testUser";
        long positionMillis = 10000L;
        String comment = "testComment";

        when(mediaFileService.getMediaFile(anyInt())).thenReturn(null);

        // Act
        boolean result = bookmarkService.setBookmark(username, mediaFileId, positionMillis, comment);

        // Assert
        assertFalse(result);
        verify(bookmarkRepository, never()).findOptByUsernameAndMediaFileId(anyString(), anyInt());
        verify(bookmarkRepository, never()).save(any());
        verify(simpMessagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    public void testSetBookmarkReturnsFalseIfNotStarted() {
        // Arrange
        int mediaFileId = 123;
        String username = "testUser";
        String comment = "testComment";
        int durationSeconds = 120; // 2 minutes

        MediaFile mediaFile = new MediaFile();
        mediaFile.setDuration((double)durationSeconds);

        when(mediaFileService.getMediaFile(anyInt())).thenReturn(mediaFile);

        // Act
        boolean result = bookmarkService.setBookmark(username, mediaFileId, 4999L, comment);

        // Assert
        assertFalse(result);
        verify(bookmarkRepository, never()).findOptByUsernameAndMediaFileId(anyString(), anyInt());
        verify(bookmarkRepository, never()).save(any());
        verify(simpMessagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), anyInt());
        verify(bookmarkRepository, never()).deleteByUsernameAndMediaFileId(anyString(), anyInt());
    }



    @Test
    public void testSetBookmarkReturnsFalseIfCloseToEnd() {
        // Arrange
        int mediaFileId = 123;
        String username = "testUser";
        String comment = "testComment";
        int durationSeconds = 120; // 2 minutes

        MediaFile mediaFile = new MediaFile();
        mediaFile.setDuration((double)durationSeconds);

        when(mediaFileService.getMediaFile(anyInt())).thenReturn(mediaFile);

        // Act
        boolean result = bookmarkService.setBookmark(username, mediaFileId, durationSeconds * 1000L - 50000L, comment);

        // Assert
        assertFalse(result);
        verify(bookmarkRepository, never()).findOptByUsernameAndMediaFileId(eq(username), eq(mediaFileId));
        verify(bookmarkRepository, never()).save(any());
        verify(simpMessagingTemplate, times(1)).convertAndSendToUser(anyString(), anyString(), anyInt());
        verify(bookmarkRepository, times(1)).deleteByUsernameAndMediaFileId(eq(username), eq(mediaFileId));
    }

    @Test
    public void testDeleteBookmark() {
        bookmarkService.deleteBookmark("testUser", 2);
        assertEquals(0, bookmarkRepository.findByUsername("testUser").size());
    }

    @Test
    public void testGetBookmarks() {
        List<Bookmark> bookmarks = new ArrayList<>();
        bookmarks.add(bookmark);
        when(bookmarkRepository.findByUsername("testUser")).thenReturn(bookmarks);
        List<Bookmark> retrievedBookmarks = bookmarkService.getBookmarks("testUser");
        assertEquals(1, retrievedBookmarks.size());
        assertEquals(retrievedBookmarks.get(0).getId(), bookmark.getId());
    }
}