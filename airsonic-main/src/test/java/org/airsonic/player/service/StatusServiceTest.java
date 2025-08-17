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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.service;

import org.airsonic.player.domain.*;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusServiceTest {

    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private PersonalSettingsService personalSettingsService;
    @Mock
    private AsyncWebSocketClient asyncWebSocketClient;
    @Mock
    private TaskSchedulingService taskService;

    @InjectMocks
    private StatusService statusService;

    private Player player;
    @Mock
    private MediaFile mediaFile;
    @Mock
    private UserSettings userSettings;

    @Nested
    class FullFilledPlayStatusTest {
        @BeforeEach
        void setUp() {
            player = new Player();
            player.setId(1);
            player.setUsername("testuser");
            player.setName("TestPlayer");

            when(mediaFile.getId()).thenReturn(100);
            when(userSettings.getNowPlayingAllowed()).thenReturn(true);
            when(personalSettingsService.getUserSettings(anyString())).thenReturn(userSettings);
        }

        @Test
        void testGetActivePlayStatuses() {
            PlayStatus active = new PlayStatus(UUID.randomUUID(), mediaFile, player, 0);
            statusService.addActiveLocalPlay(active);

            List<PlayStatus> activeStatuses = statusService.getActivePlayStatuses();
            assertTrue(activeStatuses.contains(active));
        }

        @Test
        void testGetInactivePlayStatuses() {
            PlayStatus remote = new PlayStatus(UUID.randomUUID(), mediaFile, player, 0L);

            statusService.addRemotePlay(remote);

            List<PlayStatus> inactiveStatuses = statusService.getInactivePlayStatuses();
            assertTrue(inactiveStatuses.contains(remote));
        }

        @Test
        void testGetInactivePlayStatusesWithNoRemote() {
            TransferStatus status = statusService.createStreamStatus(player);
            status.setMediaFile(mediaFile);
            statusService.removeStreamStatus(status);

            List<PlayStatus> inactiveStatuses = statusService.getInactivePlayStatuses();
            assertFalse(inactiveStatuses.isEmpty());
            PlayStatus actual = inactiveStatuses.get(0);
            assertEquals(status.getId(), actual.getTransferId());
            assertEquals(status.getMediaFile(), actual.getMediaFile());
            assertEquals(status.getPlayer(), actual.getPlayer());
        }
    }

    @Nested
    class LackedPlayStatusTest {
        @Test
        void testGetInactivePlayStatusesWithFilteredByPlayerUserName() {
            Player noUsernamePlayer = new Player();
            noUsernamePlayer.setId(2);
            noUsernamePlayer.setName("NoUsernamePlayer");

            TransferStatus status = statusService.createStreamStatus(noUsernamePlayer);
            status.setMediaFile(mediaFile);
            statusService.removeStreamStatus(status);

            List<PlayStatus> inactiveStatuses = statusService.getInactivePlayStatuses();
            assertTrue(inactiveStatuses.isEmpty());
        }

        @Test
        void testGetInactivePlayStatusesWithFilteredByNoMediaFile() {
            Player noMediaFilePlayer = new Player();
            noMediaFilePlayer.setId(3);
            noMediaFilePlayer.setName("NoMediaFilePlayer");

            TransferStatus status = statusService.createStreamStatus(noMediaFilePlayer);
            status.setMediaFile(null);
            statusService.removeStreamStatus(status);

            List<PlayStatus> inactiveStatuses = statusService.getInactivePlayStatuses();
            assertTrue(inactiveStatuses.isEmpty());
        }

        @Test
        void testGetInactivePlayStatusesWithFilteredByNotAllowed() {
            when(personalSettingsService.getUserSettings(anyString())).thenReturn(userSettings);
            when(userSettings.getNowPlayingAllowed()).thenReturn(false);

            Player notAllowedPlayer = new Player();
            notAllowedPlayer.setId(5);
            notAllowedPlayer.setName("NotAllowedPlayer");
            notAllowedPlayer.setUsername("NotAllowedUser");

            TransferStatus status = statusService.createStreamStatus(notAllowedPlayer);
            status.setMediaFile(mediaFile);
            statusService.removeStreamStatus(status);

            List<PlayStatus> inactiveStatuses = statusService.getInactivePlayStatuses();
            assertTrue(inactiveStatuses.isEmpty());
        }


    }


}
