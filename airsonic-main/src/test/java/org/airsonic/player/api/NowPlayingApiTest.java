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
package org.airsonic.player.api;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.domain.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.subsonic.restapi.NowPlaying;
import org.subsonic.restapi.NowPlayingEntry;

import jakarta.transaction.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@Transactional
public class NowPlayingApiTest extends AbstractRESTTest {

    private static final String CLIENT_NAME = "nowPlayingApiTest";

    @Mock
    PlayStatus inActivePlayStatus;

    @Mock
    PlayStatus activePlayStatus;

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getNowPlaying", "/rest/getNowPlaying.view"})
    void getNowPlaying_returnsValidJson(String endpoint) throws Exception {

        // check player not exists
        List<Player> existingPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, CLIENT_NAME);
        assertTrue(existingPlayers.isEmpty());

        when(statusService.getActivePlayStatuses()).thenReturn(List.of(activePlayStatus));
        when(statusService.getInactivePlayStatuses()).thenReturn(List.of(inActivePlayStatus));

        ArgumentCaptor<NowPlayingEntry> activePlayingEntryCaptor = ArgumentCaptor.forClass(NowPlayingEntry.class);
        ArgumentCaptor<NowPlayingEntry> inactivePlayingEntryCaptor = ArgumentCaptor.forClass(NowPlayingEntry.class);

        Player activePlayer = new Player();
        activePlayer.setId(1);
        activePlayer.setUsername("user1");
        activePlayer.setName("Active Player");
        MediaFile activeMediaFile = new MediaFile();
        when(activePlayStatus.getPlayer()).thenReturn(activePlayer);
        when(activePlayStatus.getMinutesAgo()).thenReturn(5L);
        when(activePlayStatus.getMediaFile()).thenReturn(activeMediaFile);
        when(jaxbContentService.createJaxbChild(any(), eq(activePlayer), eq(activeMediaFile), eq("user1"))).thenReturn(TestApiUtil.createTestNowPlayingEntry());

        Player inactivePlayer = new Player();
        inactivePlayer.setId(2);
        inactivePlayer.setUsername("user2");
        inactivePlayer.setName("Inactive Player");

        when(inActivePlayStatus.getPlayer()).thenReturn(inactivePlayer);
        when(inActivePlayStatus.getMinutesAgo()).thenReturn(10L);
        MediaFile inactiveMediaFile = new MediaFile();
        when(inActivePlayStatus.getMediaFile()).thenReturn(inactiveMediaFile);
        when(jaxbContentService.createJaxbChild(any(), eq(inactivePlayer), eq(inactiveMediaFile), eq("user2"))).thenReturn(TestApiUtil.createTestNowPlayingEntry());

        String response = mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andDo(print())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.version").value(AIRSONIC_API_VERSION))
            .andReturn().getResponse().getContentAsString();

        NowPlaying nowPlaying = objectMapper.readTree(response)
            .path("subsonic-response")
            .path("nowPlaying")
            .traverse(objectMapper)
            .readValueAs(NowPlaying.class);

        assertEquals(2, nowPlaying.getEntry().size());
        for (int i = 0; i < nowPlaying.getEntry().size(); i++) {
            assertNowPlayingEntry(TestApiUtil.createTestNowPlayingEntry(), nowPlaying.getEntry().get(i));
        }

        verify(jaxbContentService).createJaxbChild(activePlayingEntryCaptor.capture(), eq(activePlayer), eq(activeMediaFile), eq("user1"));
        verify(jaxbContentService).createJaxbChild(inactivePlayingEntryCaptor.capture(), eq(inactivePlayer), eq(inactiveMediaFile), eq("user2"));

        NowPlayingEntry capturedActiveEntry = activePlayingEntryCaptor.getValue();
        assertEquals("user1", capturedActiveEntry.getUsername());
        assertEquals("Active Player", capturedActiveEntry.getPlayerName());
        assertEquals(5, capturedActiveEntry.getMinutesAgo());
        assertEquals(1, capturedActiveEntry.getPlayerId());
        NowPlayingEntry capturedInactiveEntry = inactivePlayingEntryCaptor.getValue();
        assertEquals("user2", capturedInactiveEntry.getUsername());
        assertEquals("Inactive Player", capturedInactiveEntry.getPlayerName());
        assertEquals(10, capturedInactiveEntry.getMinutesAgo());
        assertEquals(2, capturedInactiveEntry.getPlayerId());

        // check player has been created
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, CLIENT_NAME);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(players.get(0), CLIENT_NAME, AIRSONIC_USER);

    }

    private void assertNowPlayingEntry(NowPlayingEntry expected, NowPlayingEntry actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getAlbum(), actual.getAlbum());
        assertEquals(expected.getArtist(), actual.getArtist());
        assertEquals(expected.isIsDir(), actual.isIsDir());
        assertEquals(expected.getYear(), actual.getYear());
        assertEquals(expected.getGenre(), actual.getGenre());
        assertEquals(expected.getDuration(), actual.getDuration());
        assertEquals(expected.getBitRate(), actual.getBitRate());
        assertEquals(expected.getTrack(), actual.getTrack());
        assertEquals(expected.getDiscNumber(), actual.getDiscNumber());
        assertEquals(expected.getSize(), actual.getSize());
        assertEquals(expected.getSuffix(), actual.getSuffix());
        assertEquals(expected.getContentType(), actual.getContentType());
        assertEquals(expected.isIsVideo(), actual.isIsVideo());
        assertEquals(expected.getPath(), actual.getPath());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getUsername(), actual.getUsername());
        assertEquals(expected.getMinutesAgo(), actual.getMinutesAgo());
        assertEquals(expected.getPlayerId(), actual.getPlayerId());
        assertEquals(expected.getPlayerName(), actual.getPlayerName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getNowPlaying", "/rest/getNowPlaying.view"})
    void getNowPlaying_returnsEmptyEntries_whenNoPlaysExist(String endpoint) throws Exception {

        // check player not exists
        List<Player> existingPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, CLIENT_NAME);
        assertTrue(existingPlayers.isEmpty());

        when(statusService.getActivePlayStatuses()).thenReturn(List.of());
        when(statusService.getInactivePlayStatuses()).thenReturn(List.of());

        // perform request
        String response = mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andDo(print())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.version").value(AIRSONIC_API_VERSION))
            .andExpect(jsonPath("$.subsonic-response.nowPlaying.entry").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        // deserialize response
        NowPlaying nowPlaying = objectMapper.readTree(response)
            .path("subsonic-response")
            .path("nowPlaying")
            .traverse(objectMapper)
            .readValueAs(NowPlaying.class);

        // assertion
        assertTrue(nowPlaying.getEntry().isEmpty());
        verify(jaxbContentService, never()).createJaxbChild(any(), any(), any(), any());

        // verify players created
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, CLIENT_NAME);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(players.get(0), CLIENT_NAME, AIRSONIC_USER);
    }
}
