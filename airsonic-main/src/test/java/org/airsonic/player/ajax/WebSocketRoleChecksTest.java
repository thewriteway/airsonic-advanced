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
package org.airsonic.player.ajax;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.service.CoverArtService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PodcastManagementService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WebSocket messages bypass the URL rules of the pages that send them, so the handlers check roles themselves.
 */
@ExtendWith(MockitoExtension.class)
public class WebSocketRoleChecksTest {

    private static final Principal PRINCIPAL = () -> "listener";

    private static User user(Role... roles) {
        User user = new User("listener", null);
        user.setRoles(Set.of(roles));
        return user;
    }

    @Nested
    class Tags {
        @Mock
        private SecurityService securityService;
        @Mock
        private MediaFileService mediaFileService;
        @Mock
        private MetaDataParserFactory metaDataParserFactory;
        @InjectMocks
        private TagWSController controller;

        @Test
        public void editingTagsRequiresCoverArtRole() {
            when(securityService.getUserByName("listener")).thenReturn(user(Role.STREAM));
            TagWSController.TagData data = new TagWSController.TagData();
            data.setMediaFileId(1);

            assertThat(controller.setTags(PRINCIPAL, data)).startsWith("Permission denied");
            verifyNoInteractions(mediaFileService, metaDataParserFactory);
        }

        @Test
        public void editingTagsRequiresFolderAccess() {
            when(securityService.getUserByName("listener")).thenReturn(user(Role.COVERART));
            MediaFile file = new MediaFile();
            when(mediaFileService.getMediaFile(1)).thenReturn(file);
            when(securityService.isFolderAccessAllowed(file, "listener")).thenReturn(false);
            TagWSController.TagData data = new TagWSController.TagData();
            data.setMediaFileId(1);

            assertThat(controller.setTags(PRINCIPAL, data)).startsWith("Media file not found");
            verifyNoInteractions(metaDataParserFactory);
        }
    }

    @Nested
    class CoverArt {
        @Mock
        private SecurityService securityService;
        @Mock
        private MediaFileService mediaFileService;
        @Mock
        private CoverArtService coverArtService;
        @InjectMocks
        private CoverArtWSController controller;

        @Test
        public void settingCoverArtRequiresCoverArtRole() throws Exception {
            when(securityService.getUserByName("listener")).thenReturn(user(Role.STREAM));
            CoverArtWSController.CoverArtSetRequest req = new CoverArtWSController.CoverArtSetRequest();
            req.setId(1);
            req.setUrl("http://example.com/cover.jpg");

            assertThat(controller.setCoverArtImage(PRINCIPAL, req)).startsWith("Permission denied");
            verify(coverArtService, never()).setCoverArtImageFromUrl(anyInt(), anyString());
        }

        @Test
        public void settingCoverArtRequiresFolderAccess() throws Exception {
            when(securityService.getUserByName("listener")).thenReturn(user(Role.COVERART));
            MediaFile dir = new MediaFile();
            when(mediaFileService.getMediaFile(1)).thenReturn(dir);
            when(securityService.isFolderAccessAllowed(dir, "listener")).thenReturn(false);
            CoverArtWSController.CoverArtSetRequest req = new CoverArtWSController.CoverArtSetRequest();
            req.setId(1);
            req.setUrl("http://example.com/cover.jpg");

            assertThat(controller.setCoverArtImage(PRINCIPAL, req)).startsWith("Media file not found");
            verify(coverArtService, never()).setCoverArtImageFromUrl(anyInt(), anyString());
        }
    }

    @Nested
    class Podcasts {
        @Mock
        private SecurityService securityService;
        @Mock
        private PodcastManagementService podcastManagementService;
        @InjectMocks
        private PodcastWSController controller;

        @Test
        public void podcastAdministrationRequiresPodcastRole() {
            when(securityService.getUserByName("listener")).thenReturn(user(Role.STREAM));

            assertThrows(AccessDeniedException.class, () -> controller.createChannel(PRINCIPAL, "http://example.com/feed"));
            assertThrows(AccessDeniedException.class, () -> controller.deleteChannels(PRINCIPAL, List.of(1)));
            assertThrows(AccessDeniedException.class, () -> controller.refreshChannels(PRINCIPAL, List.of(1)));
            verifyNoInteractions(podcastManagementService);
        }

        @Test
        public void podcastRoleMayAdministratePodcasts() {
            when(securityService.getUserByName("listener")).thenReturn(user(Role.PODCAST));

            controller.deleteChannels(PRINCIPAL, List.of(1));
            controller.refreshChannels(PRINCIPAL, List.of(1));

            verify(podcastManagementService).deleteChannel(1);
            verify(podcastManagementService).refreshChannelIds(List.of(1), true);
        }
    }
}
