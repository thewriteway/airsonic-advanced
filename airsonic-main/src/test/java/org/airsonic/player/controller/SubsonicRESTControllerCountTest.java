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

import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.ArtistService;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SecurityService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the count clamp on REST endpoints whose count sizes a query page or ends in a
 * {@code subList(0, count)}: a huge count is bounded to 500 and a negative one floored at 0.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SubsonicRESTControllerCountTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private PlayerService playerService;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private ArtistService artistService;
    @Mock
    private LastFmService lastFmService;
    @Mock
    private PodcastPersistenceService podcastPersistenceService;

    @InjectMocks
    private SubsonicRESTController controller;

    @BeforeEach
    public void setUp() throws Exception {
        when(securityService.getCurrentUsername(any())).thenReturn("alice");
        when(playerService.getPlayersForUserAndClientId(any(), any())).thenReturn(List.of(new Player()));
        when(playerService.getPlayer(any(), any(), anyString())).thenReturn(new Player());
        when(mediaFolderService.getMusicFoldersForUser(anyString())).thenReturn(List.of());
        when(mediaFileService.getMediaFile(1)).thenReturn(new MediaFile());
        when(artistService.getArtist(1)).thenReturn(new Artist());
    }

    private MockHttpServletRequest req(String count) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteUser("alice");
        request.setParameter("c", "test");
        request.setParameter("id", "1");
        request.setParameter("count", count);
        return request;
    }

    @Test
    public void getSimilarSongsClampsAbusiveCount() throws Exception {
        controller.getSimilarSongs(req(String.valueOf(Integer.MAX_VALUE)), new MockHttpServletResponse());

        verify(lastFmService).getSimilarSongsByMediaFile(any(), eq(500), any());
    }

    @Test
    public void getSimilarSongs2FloorsNegativeCount() throws Exception {
        controller.getSimilarSongs2(req("-5"), new MockHttpServletResponse());

        verify(lastFmService).getSimilarSongs(any(Artist.class), eq(0), any());
    }

    @Test
    public void getNewestPodcastsFloorsNegativeCount() throws Exception {
        controller.getNewestPodcasts(req("-5"), new MockHttpServletResponse());

        verify(podcastPersistenceService).getNewestEpisodes(0);
    }

    @Test
    public void getNewestPodcastsPassesNormalCountThrough() throws Exception {
        controller.getNewestPodcasts(req("20"), new MockHttpServletResponse());

        verify(podcastPersistenceService).getNewestEpisodes(20);
    }
}
