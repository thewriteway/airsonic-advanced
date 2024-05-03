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

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.jukebox.AudioPlayer;
import org.airsonic.player.service.jukebox.AudioPlayerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class JukeboxLegacySubsonicServiceTest {

    @Mock
    private TranscodingService transcodingService;
    @Mock
    private AudioScrobblerService audioScrobblerService;
    @Mock
    private StatusService statusService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private SecurityService securityService;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private AudioPlayerFactory audioPlayerFactory;
    @InjectMocks
    private JukeboxLegacySubsonicService jukeboxLegacySubsonicService;

    @Mock
    private Player mockedPlayer;
    @Mock
    private User mockedUser;
    @Mock
    private PlayQueue mockedPlayQueue;
    @Mock
    private MediaFile mockedMediaFile;
    @Mock
    private InputStream mockedInputStream;
    @Mock
    private AudioPlayer mockedAudioPlayer;

    @Test
    public void testUpdateJukeboxWithNotJukeBoxRoleUserShouldDoNothing() {
        // Given
        when(mockedPlayer.getUsername()).thenReturn("test");
        when(securityService.getUserByName("test")).thenReturn(mockedUser);
        when(mockedUser.isJukeboxRole()).thenReturn(false);
        // When
        jukeboxLegacySubsonicService.updateJukebox(mockedPlayer, 0);
        // Then
        verify(mockedPlayer, never()).getPlayQueue();
    }

    @Nested
    public class InitialJukeBoxTest {

        @Test
        public void testGetPosition() {
            assertEquals(0, jukeboxLegacySubsonicService.getPosition());
        }

        @Test
        public void testUpdateJukeBoxWithPlayingPlayerShouldPlay() throws Exception {
            // Given
            when(mockedPlayer.getUsername()).thenReturn("test");
            when(securityService.getUserByName("test")).thenReturn(mockedUser);
            when(mockedUser.isJukeboxRole()).thenReturn(true);
            when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
            when(mockedPlayQueue.getStatus()).thenReturn(PlayQueue.Status.PLAYING);
            when(mockedPlayQueue.getCurrentFile()).thenReturn(mockedMediaFile);
            when(mockedMediaFile.getDuration()).thenReturn(100.0);
            when(mockedMediaFile.getFileSize()).thenReturn(100L);
            when(settingsService.getJukeboxCommand()).thenReturn("juke box command");
            when(transcodingService.getTranscodedInputStream(any())).thenReturn(mockedInputStream);
            when(audioPlayerFactory.createAudioPlayer(eq(mockedInputStream), any())).thenReturn(mockedAudioPlayer);
            TransferStatus status = new TransferStatus(mockedPlayer);
            when(statusService.createStreamStatus(mockedPlayer)).thenReturn(status);

            // when
            jukeboxLegacySubsonicService.updateJukebox(mockedPlayer, 0);

            // not onSongEnd
            verify(mockedAudioPlayer, never()).close();
            // play
            verify(audioPlayerFactory).createAudioPlayer(eq(mockedInputStream), eq(jukeboxLegacySubsonicService));
            verify(mockedAudioPlayer).setGain(eq(0.75f)); // default gain
            verify(mockedAudioPlayer).play();

            // onSongStart
            verify(statusService).createStreamStatus(mockedPlayer);
            verify(mediaFileService).incrementPlayCount(mockedPlayer, mockedMediaFile);
            verify(statusService).addActiveLocalPlay(any());

            // scrobble
            verify(audioScrobblerService).register(eq(mockedMediaFile), eq("test"), eq(false), isNull());
        }

        @Test
        public void testupdateJukeBoxWithPausedPlayerShouldDoNothing() {
            // Given
            when(mockedPlayer.getUsername()).thenReturn("test");
            when(securityService.getUserByName("test")).thenReturn(mockedUser);
            when(mockedUser.isJukeboxRole()).thenReturn(true);
            when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
            when(mockedPlayQueue.getStatus()).thenReturn(PlayQueue.Status.STOPPED);
            // When
            jukeboxLegacySubsonicService.updateJukebox(mockedPlayer, 0);
            // Then
            verify(mockedPlayQueue, never()).getCurrentFile();
            assertNull(jukeboxLegacySubsonicService.getPlayer());
        }

    }

    @Nested
    public class PlayingJukeBoxTest {

        @Mock
        private Player secondPlayer;

        @Mock
        private MediaFile secondMediaFile;

        @Mock
        private AudioPlayer secondAudioPlayer;

        @BeforeEach
        public void setUp() throws Exception {
            // set player to playing
            when(mockedPlayer.getUsername()).thenReturn("test");
            when(securityService.getUserByName("test")).thenReturn(mockedUser);
            when(mockedUser.isJukeboxRole()).thenReturn(true);
            when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
            when(mockedPlayQueue.getStatus()).thenReturn(PlayQueue.Status.PLAYING);
            when(mockedPlayQueue.getCurrentFile()).thenReturn(mockedMediaFile);
            when(mockedMediaFile.getDuration()).thenReturn(100.0);
            when(mockedMediaFile.getFileSize()).thenReturn(100L);
            when(settingsService.getJukeboxCommand()).thenReturn("juke box command");
            when(transcodingService.getTranscodedInputStream(any())).thenReturn(mockedInputStream);
            when(audioPlayerFactory.createAudioPlayer(eq(mockedInputStream), any())).thenReturn(mockedAudioPlayer);
            TransferStatus status = new TransferStatus(mockedPlayer);
            when(statusService.createStreamStatus(mockedPlayer)).thenReturn(status);

            // execute initial jukebox
            jukeboxLegacySubsonicService.updateJukebox(mockedPlayer, 0);
        }

        @ParameterizedTest
        @CsvSource({
            "0, 10",
            "10, 20",
            "20, 30"
        })
        public void testGetPosition(int offset, int playerPosition) {

            // Given
            when(mockedAudioPlayer.getPosition()).thenReturn(playerPosition);
            jukeboxLegacySubsonicService.updateJukebox(mockedPlayer, offset);

            // When
            int position = jukeboxLegacySubsonicService.getPosition();

            // Then
            assertEquals(offset + playerPosition, position);

        }



        @Test
        public void testUpdateJukeBoxWithPausedPlayerShouldResume() throws Exception {
            // Given
            when(mockedPlayQueue.getStatus()).thenReturn(PlayQueue.Status.STOPPED);
            // When
            jukeboxLegacySubsonicService.updateJukebox(mockedPlayer, 0);

            // Then
            verify(mockedAudioPlayer).play();
            // no on song end
            verify(mockedAudioPlayer, never()).close();
            // no on song start
            verify(audioPlayerFactory).createAudioPlayer(any(), any());
        }

        @Test
        public void testUpdateJukeBoxWithPlayingPlayerShouldUpdatePlayer() throws Exception {
            // Given
            when(secondPlayer.getUsername()).thenReturn("test");
            when(securityService.getUserByName("test")).thenReturn(mockedUser);
            when(mockedUser.isJukeboxRole()).thenReturn(true);
            when(secondPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
            when(mockedPlayQueue.getStatus()).thenReturn(PlayQueue.Status.PLAYING);
            when(mockedPlayQueue.getCurrentFile()).thenReturn(secondMediaFile);
            when(secondMediaFile.getDuration()).thenReturn(100.0);
            when(secondMediaFile.getFileSize()).thenReturn(100L);
            when(settingsService.getJukeboxCommand()).thenReturn("juke box command");
            when(transcodingService.getTranscodedInputStream(any())).thenReturn(mockedInputStream);
            when(audioPlayerFactory.createAudioPlayer(eq(mockedInputStream), any())).thenReturn(secondAudioPlayer);
            TransferStatus status = new TransferStatus(secondPlayer);
            when(statusService.createStreamStatus(secondPlayer)).thenReturn(status);
            // When
            jukeboxLegacySubsonicService.updateJukebox(secondPlayer, 10);
            // Then
            // on song end
            verify(mockedAudioPlayer).close();
            verify(statusService).removeActiveLocalPlay(any());
            verify(statusService).removeStreamStatus(any());
            verify(audioScrobblerService).register(eq(mockedMediaFile), eq("test"), eq(true), isNull());
            // on song start
            verify(secondAudioPlayer).setGain(eq(0.75f)); // default gain
            verify(secondAudioPlayer).play();
            verify(statusService).createStreamStatus(secondPlayer);
            verify(mediaFileService).incrementPlayCount(secondPlayer, secondMediaFile);
            verify(audioScrobblerService).register(eq(secondMediaFile), eq("test"), eq(false), isNull());
        }

        @ParameterizedTest
        @ValueSource(strings = {"PAUSED", "PLAYING", "CLOSED"})
        public void testStateChangedShouldNotDoNoting(AudioPlayer.State state) {

            // when
            jukeboxLegacySubsonicService.stateChanged(mockedAudioPlayer, state);

            // then
            verify(mockedPlayQueue, never()).next();
        }

        @Test
        public void testStateChangedShouldPlayNext() throws Exception {

            // Given
            when(mockedPlayQueue.getCurrentFile()).thenReturn(secondMediaFile);
            when(secondMediaFile.getDuration()).thenReturn(100.0);
            when(secondMediaFile.getFileSize()).thenReturn(100L);

            // when
            jukeboxLegacySubsonicService.stateChanged(mockedAudioPlayer, AudioPlayer.State.EOM);

            // then
            verify(mockedPlayQueue).next();
            assertEquals(0, jukeboxLegacySubsonicService.getPosition());
           // on song end
            verify(mockedAudioPlayer).close();
            verify(statusService).removeActiveLocalPlay(any());
            verify(statusService).removeStreamStatus(any());
            verify(audioScrobblerService).register(eq(mockedMediaFile), eq("test"), eq(true), isNull());
            // on song start
            verify(mockedAudioPlayer, times(2)).setGain(eq(0.75f)); // default gain
            verify(mockedAudioPlayer, times(2)).play();
            verify(statusService, times(2)).createStreamStatus(mockedPlayer);
            verify(mediaFileService).incrementPlayCount(mockedPlayer, secondMediaFile);
            verify(audioScrobblerService).register(eq(secondMediaFile), eq("test"), eq(false), isNull());

        }

    }




}
