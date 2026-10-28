/* This file is part of Airsonic.  Airsonic is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.User;
import org.airsonic.player.security.JWTAuthenticationToken;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.service.TranscodingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;

import java.awt.Dimension;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
@ExtendWith(MockitoExtension.class)
public class StreamControllerTestCase {

    @Mock
    private StatusService statusService;
    @Mock
    private PlayerService playerService;
    @Mock
    private SecurityService securityService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private TranscodingService transcodingService;
    @Mock
    private MediaFileService mediaFileService;

    @InjectMocks
    private StreamController streamController;

    /**
     * DLNA/UPnP res URLs carry a JWT for the "anonymous" subject, which has no user row.
     * Closing such a stream must still release its stream status.
     */
    @Test
    public void testCloseAnonymousJwtStreamRemovesStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ext/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MediaFile file = new MediaFile();
        file.setId(1);
        file.setMediaType(MediaType.MUSIC);
        file.setChanged(Instant.now());

        Player player = new Player();
        player.setUsername(User.USERNAME_ANONYMOUS);
        player.setPlayQueue(new PlayQueue());
        TransferStatus status = new TransferStatus(player);

        when(securityService.getCurrentUsername(request)).thenReturn(User.USERNAME_ANONYMOUS);
        when(securityService.getUserByName(User.USERNAME_ANONYMOUS)).thenReturn(null);
        when(playerService.getPlayer(request, response, User.USERNAME_ANONYMOUS, false, true)).thenReturn(player);
        when(mediaFileService.getMediaFile(1)).thenReturn(file);
        when(transcodingService.getParameters(file, player, null, null, null))
                .thenReturn(new TranscodingService.Parameters(file, null));
        when(transcodingService.getSuffix(player, file, null)).thenReturn("mp3");
        when(statusService.createStreamStatus(player)).thenReturn(status);

        JWTAuthenticationToken authentication = new JWTAuthenticationToken(User.USERNAME_ANONYMOUS, "jwt", "ext/stream?id=1");
        ResponseEntity<Resource> result = streamController.handleRequest(authentication, null, null, null,
                Optional.empty(), Optional.of(1), Optional.empty(), null, new ServletWebRequest(request, response));

        InputStream stream = result.getBody().getInputStream();
        stream.close();

        verify(statusService).removeStreamStatus(status);
        verify(securityService, never()).incrementBytesStreamed(anyString(), anyLong());
    }

    @Test
    public void testGetRequestedVideoSize() {
        StreamController controller = new StreamController();

        // Valid spec.
        assertEquals(new Dimension(123, 456), controller.getRequestedVideoSize("123x456"), "Wrong size.");
        assertEquals(new Dimension(456, 123), controller.getRequestedVideoSize("456x123"), "Wrong size.");
        assertEquals(new Dimension(1, 1), controller.getRequestedVideoSize("1x1"), "Wrong size.");
        // Missing spec.
        assertNull(controller.getRequestedVideoSize(null), "Wrong size.");

        // Invalid spec.
        assertNull(controller.getRequestedVideoSize("123"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("123x"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("x123"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("x"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("foo123x456bar"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("foo123x456"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("123x456bar"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("fooxbar"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("-1x1"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("1x-1"), "Wrong size.");

        // Too large.
        assertNull(controller.getRequestedVideoSize("3000x100"), "Wrong size.");
        assertNull(controller.getRequestedVideoSize("100x3000"), "Wrong size.");
    }

    @Test
    public void testGetSuitableVideoSize() {

        // 4:3 aspect rate
        doTestGetSuitableVideoSize(1280, 960, 200, 416, 312);
        doTestGetSuitableVideoSize(1280, 960, 300, 416, 312);
        doTestGetSuitableVideoSize(1280, 960, 400, 480, 360);
        doTestGetSuitableVideoSize(1280, 960, 500, 480, 360);
        doTestGetSuitableVideoSize(1280, 960, 600, 480, 360);
        doTestGetSuitableVideoSize(1280, 960, 700, 480, 360);
        doTestGetSuitableVideoSize(1280, 960, 800, 640, 480);
        doTestGetSuitableVideoSize(1280, 960, 900, 640, 480);
        doTestGetSuitableVideoSize(1280, 960, 1000, 640, 480);
        doTestGetSuitableVideoSize(1280, 960, 1100, 640, 480);
        doTestGetSuitableVideoSize(1280, 960, 1200, 768, 576);
        doTestGetSuitableVideoSize(1280, 960, 1500, 768, 576);
        doTestGetSuitableVideoSize(1280, 960, 1800, 768, 576);
        doTestGetSuitableVideoSize(1280, 960, 2000, 768, 576);

        // 16:9 aspect rate
        doTestGetSuitableVideoSize(1280, 720, 200, 416, 234);
        doTestGetSuitableVideoSize(1280, 720, 300, 416, 234);
        doTestGetSuitableVideoSize(1280, 720, 400, 480, 270);
        doTestGetSuitableVideoSize(1280, 720, 500, 480, 270);
        doTestGetSuitableVideoSize(1280, 720, 600, 480, 270);
        doTestGetSuitableVideoSize(1280, 720, 700, 480, 270);
        doTestGetSuitableVideoSize(1280, 720, 800, 640, 360);
        doTestGetSuitableVideoSize(1280, 720, 900, 640, 360);
        doTestGetSuitableVideoSize(1280, 720, 1000, 640, 360);
        doTestGetSuitableVideoSize(1280, 720, 1100, 640, 360);
        doTestGetSuitableVideoSize(1280, 720, 1200, 768, 432);
        doTestGetSuitableVideoSize(1280, 720, 1500, 768, 432);
        doTestGetSuitableVideoSize(1280, 720, 1800, 768, 432);
        doTestGetSuitableVideoSize(1280, 720, 2000, 768, 432);

        // Small original size.
        doTestGetSuitableVideoSize(100, 100, 1000, 100, 100);
        doTestGetSuitableVideoSize(100, 1000, 1000, 100, 1000);
        doTestGetSuitableVideoSize(1000, 100, 100, 1000, 100);

        // Unknown original size.
        doTestGetSuitableVideoSize(720, null, 200, 416, 234);
        doTestGetSuitableVideoSize(null, 540, 300, 416, 234);
        doTestGetSuitableVideoSize(null, null, 400, 480, 270);
        doTestGetSuitableVideoSize(720, null, 500, 480, 270);
        doTestGetSuitableVideoSize(null, 540, 600, 480, 270);
        doTestGetSuitableVideoSize(null, null, 700, 480, 270);
        doTestGetSuitableVideoSize(720, null, 1200, 768, 432);
        doTestGetSuitableVideoSize(null, 540, 1500, 768, 432);
        doTestGetSuitableVideoSize(null, null, 2000, 768, 432);

        // Odd original size.
        doTestGetSuitableVideoSize(203, 101, 1500, 204, 102);
        doTestGetSuitableVideoSize(464, 853, 1500, 464, 854);
    }

    private void doTestGetSuitableVideoSize(Integer existingWidth, Integer existingHeight, Integer maxBitRate,
            int expectedWidth, int expectedHeight) {
        Dimension dimension = TranscodingService.getSuitableVideoSize(existingWidth, existingHeight, maxBitRate);
        assertEquals(expectedWidth, dimension.width, "Wrong Width");
        assertEquals(expectedHeight, dimension.height, "Wrong Height");
    }
}
