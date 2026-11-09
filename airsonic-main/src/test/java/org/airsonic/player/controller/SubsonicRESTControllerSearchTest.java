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
import org.airsonic.player.service.AlbumService;
import org.airsonic.player.service.ArtistService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
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
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock
    private ArtistService artistService;
    @Mock
    private AlbumService albumService;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private SubsonicRESTController controller;

    // (count, offset) snapshotted at each searchService.search invocation.
    private final List<int[]> searchArgs = new ArrayList<>();

    // (count, offset) received by the search3 empty-query database enumeration.
    private final List<int[]> artistArgs = new ArrayList<>();
    private final List<int[]> albumArgs = new ArrayList<>();
    private final List<int[]> songArgs = new ArrayList<>();

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
        when(artistService.getArtists(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            artistArgs.add(new int[] {invocation.getArgument(1), invocation.getArgument(2)});
            return List.of();
        });
        when(albumService.getAlbums(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            albumArgs.add(new int[] {invocation.getArgument(1), invocation.getArgument(2)});
            return List.of();
        });
        when(mediaFileService.getSongs(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            songArgs.add(new int[] {invocation.getArgument(1), invocation.getArgument(2)});
            return List.of();
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

    @Test
    public void search3EmptyQueryPassesDeepOffsetsToDatabaseEnumeration() throws Exception {
        // the empty-query branch enumerates the database (SQL OFFSET), where a deep offset is
        // legitimate pagination, not a Lucene allocation
        MockHttpServletRequest request = req();
        request.setParameter("query", "\"\"");
        setAllCountsAndOffsets(request, "500", "2000");

        controller.search3(request, new MockHttpServletResponse());

        assertThat(searchArgs).isEmpty(); // never reaches Lucene
        assertThat(artistArgs).containsExactly(new int[] {500, 2000});
        assertThat(albumArgs).containsExactly(new int[] {500, 2000});
        assertThat(songArgs).containsExactly(new int[] {500, 2000});
    }

    @Test
    public void search3EmptyQueryAcceptsMaximumOffset() throws Exception {
        // OffsetBasedPageRequest stores the offset as a long and does no int arithmetic on it
        MockHttpServletRequest request = req();
        request.setParameter("query", "\"\"");
        setAllCountsAndOffsets(request, "500", String.valueOf(Integer.MAX_VALUE));

        controller.search3(request, new MockHttpServletResponse());

        assertThat(artistArgs).containsExactly(new int[] {500, Integer.MAX_VALUE});
        assertThat(albumArgs).containsExactly(new int[] {500, Integer.MAX_VALUE});
        assertThat(songArgs).containsExactly(new int[] {500, Integer.MAX_VALUE});
    }

    @Test
    public void search3EmptyQueryFloorsNegativeOffsetToZero() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("query", "\"\"");
        setAllCountsAndOffsets(request, "500", "-100");

        controller.search3(request, new MockHttpServletResponse());

        assertThat(artistArgs).containsExactly(new int[] {500, 0});
        assertThat(albumArgs).containsExactly(new int[] {500, 0});
        assertThat(songArgs).containsExactly(new int[] {500, 0});
    }

    @Test
    public void search3WithQueryStillClampsOffset() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("artistCount", "500");
        request.setParameter("artistOffset", "2000");

        controller.search3(request, new MockHttpServletResponse());

        assertThat(searchArgs).hasSize(3);
        assertThat(searchArgs.get(0)[1]).isEqualTo(500); // artist criteria is first in search3
        assertThat(artistArgs).isEmpty();
        assertThat(albumArgs).isEmpty();
        assertThat(songArgs).isEmpty();
    }

    @Test
    public void search3EmptyQueryStopsAtApiListLimits() throws Exception {
        when(settingsService.getRestArtistLimit()).thenReturn(1000);
        when(settingsService.getRestAlbumLimit()).thenReturn(1200);
        MockHttpServletRequest request = req();
        request.setParameter("query", "\"\"");
        setAllCountsAndOffsets(request, "500", "900");

        controller.search3(request, new MockHttpServletResponse());

        assertThat(artistArgs).containsExactly(new int[] {100, 900});
        assertThat(albumArgs).containsExactly(new int[] {300, 900});
        assertThat(songArgs).containsExactly(new int[] {500, 900}); // songs are not limited

        artistArgs.clear();
        albumArgs.clear();
        request.setParameter("artistOffset", "1000");
        request.setParameter("albumOffset", "1500");
        controller.search3(request, new MockHttpServletResponse());

        assertThat(artistArgs).isEmpty();
        assertThat(albumArgs).isEmpty();
    }

    @Test
    public void search3WithQueryIgnoresApiListLimits() throws Exception {
        when(settingsService.getRestArtistLimit()).thenReturn(10);
        when(settingsService.getRestAlbumLimit()).thenReturn(10);
        MockHttpServletRequest request = req();
        setAllCountsAndOffsets(request, "100", "50");

        controller.search3(request, new MockHttpServletResponse());

        assertThat(searchArgs).hasSize(3).allSatisfy(args -> assertThat(args).containsExactly(100, 50));
    }

    @Test
    public void getAlbumList2StopsAtAlbumLimit() throws Exception {
        when(settingsService.getRestAlbumLimit()).thenReturn(1200);
        MockHttpServletRequest request = req();
        request.setParameter("type", "alphabeticalByName");
        request.setParameter("size", "500");
        request.setParameter("offset", "1000");

        controller.getAlbumList2(request, new MockHttpServletResponse());
        verify(albumService).getAlphabeticalAlbums(eq(1000), eq(200), eq(false), eq(false), any());

        request.setParameter("offset", "1200");
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.getAlbumList2(request, response);
        verify(albumService, never()).getAlphabeticalAlbums(eq(1200), anyInt(), anyBoolean(), anyBoolean(), any());
        assertThat(response.getContentAsString()).contains("albumList2").doesNotContain("<album ");
    }

    @Test
    public void getAlbumListStopsAtAlbumLimit() throws Exception {
        when(settingsService.getRestAlbumLimit()).thenReturn(1200);
        MockHttpServletRequest request = req();
        request.setParameter("type", "newest");
        request.setParameter("size", "500");
        request.setParameter("offset", "1000");

        controller.getAlbumList(request, new MockHttpServletResponse());
        verify(mediaFileService).getNewestAlbums(eq(1000), eq(200), any());

        request.setParameter("offset", "5000");
        controller.getAlbumList(request, new MockHttpServletResponse());
        verify(mediaFileService, never()).getNewestAlbums(eq(5000), anyInt(), any());
    }

    @Test
    public void limitIndexKeepsFirstArtistsAndDropsEmptyLetters() {
        TreeMap<String, List<String>> index = new TreeMap<>(Map.of(
                "A", List.of("a1", "a2"), "B", List.of("b1", "b2"), "C", List.of("c1")));

        assertThat(SubsonicRESTController.limitIndex(index, 3))
                .containsExactly(Map.entry("A", List.of("a1", "a2")), Map.entry("B", List.of("b1")));
        assertThat(SubsonicRESTController.limitIndex(index, 2)).containsOnlyKeys("A");
        assertThat(SubsonicRESTController.limitIndex(index, 0)).isSameAs(index);
        assertThat(SubsonicRESTController.limitIndex(index, 100)).isEqualTo(index);
    }

    @Test
    public void limitPageSizeEndsPagesAtTheLimit() {
        assertThat(SubsonicRESTController.limitPageSize(0, 500, 0)).isEqualTo(500);
        assertThat(SubsonicRESTController.limitPageSize(4800, 500, 5000)).isEqualTo(200);
        assertThat(SubsonicRESTController.limitPageSize(5000, 500, 5000)).isZero();
        assertThat(SubsonicRESTController.limitPageSize(9000, 500, 5000)).isZero();
        assertThat(SubsonicRESTController.limitPageSize(-10, 500, 100)).isEqualTo(100);
    }

    private static void setAllCountsAndOffsets(MockHttpServletRequest request, String count, String offset) {
        for (String type : List.of("artist", "album", "song")) {
            request.setParameter(type + "Count", count);
            request.setParameter(type + "Offset", offset);
        }
    }
}
