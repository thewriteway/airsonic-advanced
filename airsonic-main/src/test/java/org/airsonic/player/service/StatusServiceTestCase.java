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
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import org.airsonic.player.ajax.NowPlayingInfo;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit test of {@link StatusService}.
 *
 * @author Sindre Mehus
 */
@ExtendWith(MockitoExtension.class)
public class StatusServiceTestCase {

    private Player player1;
    private Player player2;

    @Mock
    private AsyncWebSocketClient asyncWebSocketClient;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private PersonalSettingsService personalSettingsService;
    @Mock
    private UserSettings settings;
    @Mock
    private TaskSchedulingService taskService;
    @InjectMocks
    private StatusService service;

    @BeforeEach
    public void setUp() {
        doReturn(new MediaFile()).when(mediaFileService).getMediaFile(any(Path.class));
        doReturn(settings).when(personalSettingsService).getUserSettings(any(String.class));
        doReturn(true).when(settings).getNowPlayingAllowed();
        player1 = new Player();
        player1.setId(1);
        player1.setUsername("p1");
        player2 = new Player();
        player2.setId(2);
        player2.setUsername("p2");
    }

    @Test
    public void testSimpleAddRemoveTransferStatus() {
        TransferStatus status = service.createStreamStatus(player1);
        status.setExternalFile(Paths.get("bla"));
        assertTrue(status.isActive());
        assertTrue(service.getAllStreamStatuses().contains(status));
        assertTrue(service.getStreamStatusesForPlayer(player1).contains(status));
        assertTrue(service.getStreamStatusesForPlayer(player2).isEmpty());
        assertTrue(service.getInactivePlayStatuses().isEmpty());
        // won't start until file starts playing
        assertTrue(service.getActivePlayStatuses().isEmpty());
        verifyNoInteractions(asyncWebSocketClient);

        service.removeStreamStatus(status);
        assertFalse(status.isActive());
        assertTrue(service.getAllStreamStatuses().contains(status));
        assertTrue(service.getStreamStatusesForPlayer(player1).isEmpty());
        assertTrue(service.getStreamStatusesForPlayer(player2).isEmpty());
        assertFalse(service.getInactivePlayStatuses().isEmpty());
        assertTrue(service.getActivePlayStatuses().isEmpty());
        verify(asyncWebSocketClient, timeout(300)).send(eq("/topic/nowPlaying/recent/add"), any(NowPlayingInfo.class));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testSimpleAddRemovePlayStatus() {
        PlayStatus status = new PlayStatus(UUID.randomUUID(), new MediaFile(), player1, 0);
        service.addActiveLocalPlay(status);
        assertTrue(service.getInactivePlayStatuses().isEmpty());
        assertFalse(service.getActivePlayStatuses().isEmpty());
        verify(asyncWebSocketClient, timeout(300)).send(eq("/topic/nowPlaying/current/add"), any(NowPlayingInfo.class));

        service.removeActiveLocalPlay(status);
        assertTrue(service.getInactivePlayStatuses().isEmpty());
        assertTrue(service.getActivePlayStatuses().isEmpty());
        verify(asyncWebSocketClient, timeout(300)).send(eq("/topic/nowPlaying/current/remove"), any(NowPlayingInfo.class));

        service.addRemotePlay(status);
        assertFalse(service.getInactivePlayStatuses().isEmpty());
        assertTrue(service.getActivePlayStatuses().isEmpty());
        verify(asyncWebSocketClient, timeout(300)).send(eq("/topic/nowPlaying/recent/add"), any(NowPlayingInfo.class));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testNoBroadcast() {
        // No media file
        TransferStatus tStatus = service.createStreamStatus(player1);
        tStatus.setExternalFile(Paths.get("noFile"));
        doReturn(null).when(mediaFileService).getMediaFile(eq(Paths.get("noFile")));
        service.removeStreamStatus(tStatus);

        PlayStatus pStatus = new PlayStatus(UUID.randomUUID(), null, player1, 0);
        service.addActiveLocalPlay(pStatus);
        service.removeActiveLocalPlay(pStatus);
        service.addRemotePlay(pStatus);

        // Old status
        pStatus = new PlayStatus(UUID.randomUUID(), new MediaFile(), player1, Instant.now().minus(75, ChronoUnit.MINUTES));
        service.addActiveLocalPlay(pStatus);
        service.removeActiveLocalPlay(pStatus);
        service.addRemotePlay(pStatus);

        // User settings
        doReturn(false).when(settings).getNowPlayingAllowed();

        pStatus = new PlayStatus(UUID.randomUUID(), new MediaFile(), player1, 0);
        service.addActiveLocalPlay(pStatus);
        service.removeActiveLocalPlay(pStatus);
        service.addRemotePlay(pStatus);

        tStatus = service.createStreamStatus(player1);
        tStatus.setExternalFile(Paths.get("bla"));
        service.removeStreamStatus(tStatus);

        // Verify
        verifyNoInteractions(asyncWebSocketClient);
    }

    @Test
    public void testMultipleStreamsSamePlayer() {
        TransferStatus statusA = service.createStreamStatus(player1);
        statusA.setExternalFile(Paths.get("bla"));
        TransferStatus statusB = service.createStreamStatus(player1);
        statusB.setExternalFile(Paths.get("bla"));

        assertTrue(service.getAllStreamStatuses().contains(statusA));
        assertTrue(service.getAllStreamStatuses().contains(statusB));
        assertTrue(service.getStreamStatusesForPlayer(player1).contains(statusA));
        assertTrue(service.getStreamStatusesForPlayer(player1).contains(statusB));

        // Stop stream A.
        service.removeStreamStatus(statusA);
        assertFalse(statusA.isActive());
        assertTrue(statusB.isActive());
        assertTrue(service.getAllStreamStatuses().contains(statusB));
        assertTrue(service.getStreamStatusesForPlayer(player1).contains(statusB));

        // Stop stream B.
        service.removeStreamStatus(statusB);
        assertFalse(statusB.isActive());
        assertTrue(service.getAllStreamStatuses().contains(statusB));
        assertTrue(service.getStreamStatusesForPlayer(player1).isEmpty());

        // Start stream C.
        TransferStatus statusC = service.createStreamStatus(player1);
        assertTrue(statusC.isActive());
        assertTrue(service.getAllStreamStatuses().contains(statusC));
        assertTrue(service.getStreamStatusesForPlayer(player1).contains(statusC));
    }
}
