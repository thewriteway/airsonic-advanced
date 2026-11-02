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
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.AudioScrobblerService;
import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.StatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Play counts and bookmarks set through the REST API.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SubsonicRESTControllerScrobbleTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private PlayerService playerService;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private StatusService statusService;
    @Mock
    private AudioScrobblerService audioScrobblerService;
    @Mock
    private BookmarkService bookmarkService;
    @InjectMocks
    private SubsonicRESTController controller;

    private final Player player = new Player();
    private final MediaFile file = new MediaFile();

    @BeforeEach
    public void setUp() throws Exception {
        player.setUsername("alice");
        when(securityService.getCurrentUsername(any())).thenReturn("alice");
        when(playerService.getPlayer(any(), any(), anyString())).thenReturn(player);
        when(mediaFileService.getMediaFile(1)).thenReturn(file);
    }

    private MockHttpServletRequest req() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteUser("alice");
        request.setParameter("c", "test");
        request.setParameter("id", "1");
        return request;
    }

    @Test
    public void nowPlayingNotificationIsNotAPlay() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("submission", "false");

        controller.scrobble(request, new MockHttpServletResponse());

        verify(mediaFileService, never()).incrementPlayCount(any(), any());
        verify(audioScrobblerService).register(file, "alice", false, null);
    }

    @Test
    public void submissionIsAPlay() throws Exception {
        controller.scrobble(req(), new MockHttpServletResponse());

        verify(mediaFileService).incrementPlayCount(player, file);
        verify(audioScrobblerService).register(file, "alice", true, null);
    }

    @Test
    public void createBookmarkSavesTheExactPosition() throws Exception {
        when(securityService.isFolderAccessAllowed(file, "alice")).thenReturn(true);
        when(bookmarkService.createBookmark(anyString(), anyInt(), anyLong(), any())).thenReturn(true);
        MockHttpServletRequest request = req();
        request.setParameter("position", "0");

        controller.createBookmark(request, new MockHttpServletResponse());

        verify(bookmarkService).createBookmark(eq("alice"), eq(1), eq(0L), any());
        verify(bookmarkService, never()).setBookmark(anyString(), anyInt(), anyLong(), any());
    }

    @Test
    public void createBookmarkRefusesInaccessibleFiles() throws Exception {
        when(securityService.isFolderAccessAllowed(file, "alice")).thenReturn(false);
        MockHttpServletRequest request = req();
        request.setParameter("position", "60000");

        controller.createBookmark(request, new MockHttpServletResponse());

        verify(bookmarkService, never()).createBookmark(anyString(), anyInt(), anyLong(), any());
    }
}
