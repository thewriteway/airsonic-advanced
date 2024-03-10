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

import org.airsonic.player.command.PodcastSettingsCommand.PodcastRule;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastChannelRule;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PodcastManagementServiceTestCase {

    @Mock
    private SettingsService settingsService;
    @Mock
    private TaskSchedulingService taskService;
    @Mock
    private AsyncWebSocketClient asyncWebSocketClient;
    @Mock
    private PodcastPersistenceService podcastPersistenceService;

    @InjectMocks
    @Spy
    private PodcastManagementService podcastManagementService;

    @Mock
    private PodcastEpisode mockedEpisode;
    @Mock
    private MediaFile mockedMediaFile;
    @Mock
    private MusicFolder mockedMusicFolder;
    @Mock
    private PodcastChannel mockedChannel;
    @Mock
    private CloseableHttpClient mockedHttpClient;
    @Mock
    private CloseableHttpResponse mockedHttpResponse;
    @Mock
    private HttpEntity mockedHttpEntity;

    @TempDir
    private Path tempFolder;

    // test data
    private final PodcastChannelRule RULE_SCHEDULE = new PodcastChannelRule(1, 1, 1, 1);
    private final PodcastRule RULE_SCHEDULE_COMMAND = new PodcastRule(RULE_SCHEDULE, "test");
    private final PodcastChannelRule RULE_UNSCHEDULE = new PodcastChannelRule(2, -1, null, null);

    @Test
    void testCreateOrUpdateChannelRule() {
        // given
        // prepare instant
        when(podcastPersistenceService.createOrUpdateChannelRuleByCommand(RULE_SCHEDULE_COMMAND)).thenReturn(RULE_SCHEDULE);
        Instant now = Instant.parse("2020-01-01T00:00:00Z");
        Instant expectedFirstTime = Instant.parse("2020-01-01T00:05:00Z");

        try (MockedStatic<Instant> mockedInstant = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(now);
            // when
            podcastManagementService.createOrUpdateChannelRuleByCommand(RULE_SCHEDULE_COMMAND);

            // then
            verify(taskService).scheduleAtFixedRate(eq("podcast-channel-refresh-1"), any(), eq(expectedFirstTime), eq(Duration.ofHours(1)), eq(true));
        }
    }

    @Test
    void testDeleteChannel() throws Exception {

        // given
        int channelId = 1;
        when(podcastPersistenceService.deleteChannel(channelId)).thenReturn(true);

        // when
        podcastManagementService.deleteChannel(channelId);

        // then
        verify(asyncWebSocketClient).send("/topic/podcasts/deleted", channelId);
    }

    @Test
    void testDeleteChannelRule() {
        // given
        when(podcastPersistenceService.deleteChannelRule(10)).thenReturn(true);

        // when
        podcastManagementService.deleteChannelRule(10);

        // then
        assertUnschedule(10); // assert call unschedule
    }

    @Test
    void testSchedule() {
        // given
        when(podcastPersistenceService.getAllChannelRules()).thenReturn(Arrays.asList(RULE_SCHEDULE, RULE_UNSCHEDULE));

        // config scheduleDefault to schedule
        when(settingsService.getPodcastUpdateInterval()).thenReturn(1);

        // prepare instant
        Instant now = Instant.parse("2020-01-01T00:00:00Z");
        Instant expectedFirstTime = Instant.parse("2020-01-01T00:05:00Z");


        try (MockedStatic<Instant> mockedInstant = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(now);

            // when
            podcastManagementService.schedule();

            // then
            verify(taskService).scheduleAtFixedRate(eq("podcast-channel-refresh--1"), any(), eq(expectedFirstTime), eq(Duration.ofHours(1)), eq(true)); // assert scheduleDefault is called
            assertUnschedule(2); // schedule rule 2
            verify(taskService).scheduleAtFixedRate(eq("podcast-channel-refresh-1"), any(), eq(expectedFirstTime), eq(Duration.ofHours(1)), eq(true));
        }
    }

    @Test
    void testScheduleDefaultWithoutIntervalConfigShouldUnshcedule() {
        // given
        when(settingsService.getPodcastUpdateInterval()).thenReturn(-1);

        // when
        podcastManagementService.scheduleDefault();

        // then
        // unschedule should be called
        assertUnschedule(-1);
    }

    private void assertUnschedule(Integer id) {
        verify(taskService).unscheduleTask(eq("podcast-channel-refresh-" + id));
    }


    @Test
    void testScheduleDefaultWithIntervalConfigShouldSchedule() {
        // given
        when(settingsService.getPodcastUpdateInterval()).thenReturn(1);
        Instant now = Instant.parse("2020-01-01T00:00:00Z");
        Instant expectedFirstTime = Instant.parse("2020-01-01T00:05:00Z");

        // when
        try (MockedStatic<Instant> mockedInstant = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(now);
            podcastManagementService.scheduleDefault();

            // then
            verify(taskService).scheduleAtFixedRate(eq("podcast-channel-refresh--1"), any(), eq(expectedFirstTime), eq(Duration.ofHours(1)), eq(true));
            verify(taskService, never()).unscheduleTask(anyString());
        }
    }

}
