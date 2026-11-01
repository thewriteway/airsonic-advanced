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

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.SearchCriteria;
import org.airsonic.player.domain.SearchResult;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SearchService;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit test for the count/offset clamp on the search endpoints. Drives the controller with abusive
 * and legitimate parameter values and snapshots the {@link SearchCriteria} count/offset actually
 * handed to {@link SearchService} (search2/search3 reuse one mutable criteria across three calls,
 * so the values are snapshotted per invocation rather than captured by reference).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SubsonicRESTControllerSearchTest {

    @Mock
    private SearchService searchService;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private SecurityService securityService;
    @Mock
    private JaxbContentService jaxbContentService;
    @Mock
    private PlayerService playerService;

    @InjectMocks
    private SubsonicRESTController controller;

    // (count, offset) snapshotted at each searchService.search invocation.
    private final List<int[]> searchArgs = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        when(securityService.getCurrentUsername(any())).thenReturn("alice");
        when(playerService.getPlayersForUserAndClientId(any(), any())).thenReturn(List.of(new Player()));
        when(playerService.getPlayer(any(), any(), anyString())).thenReturn(new Player());
        when(mediaFolderService.getMusicFoldersForUser(anyString())).thenReturn(List.of());
        when(mediaFolderService.getMusicFoldersForUser(anyString(), any())).thenReturn(List.of());
        when(searchService.search(any(SearchCriteria.class), any(), any())).thenAnswer(invocation -> {
            SearchCriteria c = invocation.getArgument(0);
            searchArgs.add(new int[] {c.getCount(), c.getOffset()});
            return new SearchResult();
        });
    }

    private MockHttpServletRequest req() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteUser("alice");
        request.setParameter("c", "test");
        request.setParameter("query", "anything");
        return request;
    }

    @Test
    public void searchClampsAbusiveCountAndOffset() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("count", String.valueOf(Integer.MAX_VALUE));
        request.setParameter("offset", String.valueOf(Integer.MAX_VALUE));

        controller.search(request, new MockHttpServletResponse());

        assertThat(searchArgs).hasSize(1);
        assertThat(searchArgs.get(0)).containsExactly(500, 500);
    }

    @Test
    public void search2ClampsAllThreeCountParams() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("artistCount", String.valueOf(Integer.MAX_VALUE));
        request.setParameter("albumCount", String.valueOf(Integer.MAX_VALUE));
        request.setParameter("songCount", String.valueOf(Integer.MAX_VALUE));

        controller.search2(request, new MockHttpServletResponse());

        // artist, album, song: each clamped to 500
        assertThat(searchArgs).hasSize(3);
        assertThat(searchArgs).allSatisfy(a -> assertThat(a[0]).isEqualTo(500));
    }

    @Test
    public void search3ClampsAllThreeCountParams() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("artistCount", String.valueOf(Integer.MAX_VALUE));
        request.setParameter("albumCount", String.valueOf(Integer.MAX_VALUE));
        request.setParameter("songCount", String.valueOf(Integer.MAX_VALUE));

        controller.search3(request, new MockHttpServletResponse());

        assertThat(searchArgs).hasSize(3);
        assertThat(searchArgs).allSatisfy(a -> assertThat(a[0]).isEqualTo(500));
    }

    @Test
    public void searchClampsAbusiveOffsetEvenWithSmallCount() throws Exception {
        // the offset is added to count at the allocation point, so an abusive offset alone (which
        // would overflow offset + count to a negative) is clamped independently
        MockHttpServletRequest request = req();
        request.setParameter("count", "20");
        request.setParameter("offset", String.valueOf(Integer.MAX_VALUE));

        controller.search(request, new MockHttpServletResponse());

        assertThat(searchArgs.get(0)).containsExactly(20, 500);
    }

    @Test
    public void searchClampsNegativeCountAndOffsetToZero() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("count", "-5");
        request.setParameter("offset", "-100");

        controller.search(request, new MockHttpServletResponse());

        assertThat(searchArgs.get(0)).containsExactly(0, 0);
    }

    @Test
    public void searchPassesLegitimateCountThroughUnchanged() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("count", "100");
        request.setParameter("offset", "40");

        controller.search(request, new MockHttpServletResponse());

        assertThat(searchArgs.get(0)).containsExactly(100, 40);
    }

    @Test
    public void clampSearchCountBoundsToZeroAndMax() {
        assertThat(SubsonicRESTController.clampSearchCount(Integer.MAX_VALUE)).isEqualTo(500);
        assertThat(SubsonicRESTController.clampSearchCount(501)).isEqualTo(500);
        assertThat(SubsonicRESTController.clampSearchCount(500)).isEqualTo(500);
        assertThat(SubsonicRESTController.clampSearchCount(100)).isEqualTo(100);
        assertThat(SubsonicRESTController.clampSearchCount(0)).isEqualTo(0);
        assertThat(SubsonicRESTController.clampSearchCount(-1)).isEqualTo(0);
        assertThat(SubsonicRESTController.clampSearchCount(Integer.MIN_VALUE)).isEqualTo(0);
    }
}
