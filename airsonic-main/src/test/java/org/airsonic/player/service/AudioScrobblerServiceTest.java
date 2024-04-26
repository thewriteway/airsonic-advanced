/*
 * This file is part of Airsonic
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Airsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 * Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.scrobbler.LastFMScrobbler;
import org.airsonic.player.service.scrobbler.ListenBrainzScrobbler;
import org.airsonic.player.util.StringUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioScrobblerServiceTest {

    @Mock
    private PersonalSettingsService personalSettingsService;
    @Mock
    private SecurityService securityService;
    @Mock
    private LastFMScrobbler lastFMScrobbler;
    @Mock
    private ListenBrainzScrobbler listenBrainzScrobbler;
    @InjectMocks
    private AudioScrobblerService audioScrobblerService;

    @Mock
    private MediaFile mockedMediaFile;
    @Mock
    private UserSettings mockedUserSettings;

    private UserCredential lastFmCredential = new UserCredential(new User("testUser", "test@example.com"), "lastFmUser", StringUtil.utf8HexEncode("lastFmPassword"), "hex", App.LASTFM);

    private UserCredential listenBreinzCredential = new UserCredential(new User("testUser", "test@example.com"), "listenBreinz", StringUtil.utf8HexEncode("listenBrainzPassword"), "hex", App.LISTENBRAINZ);

    @Test
    void registerWithNullMediaFileShouldNotDoNothing() {
        // Arrange
        String username = "testUser";
        boolean submission = true;
        Instant time = Instant.now();

        // Act
        audioScrobblerService.register(null, username, submission, time);

        // Assert
        verify(personalSettingsService, never()).getUserSettings(anyString());
    }

    @Test
    void registerWithVideoShouldNotDoNothing() {
        // Arrange
        String username = "testUser";
        boolean submission = true;
        Instant time = Instant.now();
        when(mockedMediaFile.isVideo()).thenReturn(true);

        // Act
        audioScrobblerService.register(mockedMediaFile, username, submission, time);

        // Assert
        verify(personalSettingsService, never()).getUserSettings(anyString());
    }


    @ParameterizedTest
    @CsvSource({
        "true, true",
        "true, false",
        "false, true",
        "false, false"
    })
    void registerShouldRegister(boolean lastFmEnabled, boolean listenBrainzEnabled) {
        // Arrange
        String username = "testUser";
        boolean submission = true;
        Instant time = Instant.now();
        when(mockedMediaFile.isVideo()).thenReturn(false);
        when(personalSettingsService.getUserSettings(ArgumentMatchers.eq(username))).thenReturn(mockedUserSettings);
        when(mockedUserSettings.getLastFmEnabled()).thenReturn(lastFmEnabled);
        when(mockedUserSettings.getListenBrainzEnabled()).thenReturn(listenBrainzEnabled);
        Map<App, UserCredential> creds = new HashMap<>();
        if (lastFmEnabled) {
            creds.put(App.LASTFM, lastFmCredential);
        }
        if (listenBrainzEnabled) {
            creds.put(App.LISTENBRAINZ, listenBreinzCredential);
            when(mockedUserSettings.getListenBrainzUrl()).thenReturn("listenBrainzUrl");
        }
        doReturn(creds).when(securityService).getDecodableCredsForApps(eq(username), any(App[].class));

        // Act
        audioScrobblerService.register(mockedMediaFile, username, submission, time);

        // Assert
        verify(lastFMScrobbler, times(lastFmEnabled ? 1 : 0)).register(eq(mockedMediaFile), eq("lastFmUser"), anyString(), eq(submission), eq(time));
        verify(listenBrainzScrobbler, times(listenBrainzEnabled ? 1 : 0)).register(eq(mockedMediaFile), eq("listenBrainzUrl"), anyString(), eq(submission), eq(time));
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "true, false",
        "false, true",
        "false, false"
    })
    void registerWithoutCredsShouldNotRegister(boolean lastFmEnabled, boolean listenBrainzEnabled) {
        // Arrange
        String username = "testUser";
        boolean submission = true;
        Instant time = Instant.now();
        when(mockedMediaFile.isVideo()).thenReturn(false);
        when(personalSettingsService.getUserSettings(ArgumentMatchers.eq(username))).thenReturn(mockedUserSettings);
        when(mockedUserSettings.getLastFmEnabled()).thenReturn(lastFmEnabled);
        when(mockedUserSettings.getListenBrainzEnabled()).thenReturn(listenBrainzEnabled);
        Map<App, UserCredential> creds = new HashMap<>();
        when(securityService.getDecodableCredsForApps(eq(username), any(App[].class))).thenReturn(creds);

        // Act
        audioScrobblerService.register(mockedMediaFile, username, submission, time);

        // Assert
        verify(lastFMScrobbler, never()).register(any(), anyString(), anyString(), eq(submission), eq(time));
        verify(listenBrainzScrobbler, never()).register(any(), anyString(), anyString(), eq(submission), eq(time));
    }



}
