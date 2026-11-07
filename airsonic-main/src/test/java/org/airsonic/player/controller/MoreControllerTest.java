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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {MoreController.class})
class MoreControllerTest {

    /** The same as the default in {@link SettingsService}, which is private. */
    private static final String DEFAULT_PATTERN = "%{['USER_MUSIC_FOLDERS'][0]}/Incoming";
    private static final String USERNAME = "testuser";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettingsService settingsService;

    @MockitoBean
    private SecurityService securityService;

    @MockitoBean
    private PlayerService playerService;

    @MockitoBean
    private MediaFileService mediaFileService;

    @MockitoBean
    private MediaFolderService mediaFolderService;

    // starting the context reads airsonic.home, whose default folder does not exist on build machines
    @TempDir
    private static Path tempDir;

    @BeforeAll
    static void setHome() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @AfterAll
    static void clearHome() {
        System.clearProperty("airsonic.home");
    }

    @BeforeEach
    void setUp() throws Exception {
        User user = new User(USERNAME, "testuser@example.com");
        user.setRoles(Set.of(User.Role.UPLOAD));
        when(securityService.getCurrentUser(any())).thenReturn(user);
        when(playerService.getPlayer(any(), any(), eq(USERNAME))).thenReturn(new Player());
        when(settingsService.buildSpelContext()).thenAnswer(invocation -> new HashMap<String, Object>());
        when(settingsService.resolveContextualString(any(), any())).thenCallRealMethod();
        when(settingsService.getUploadsFolder()).thenReturn(DEFAULT_PATTERN);
    }

    @Test
    @WithMockUser(username = USERNAME)
    void suggestsTheFirstMusicFolderOfTheUser() throws Exception {
        Path folder = Path.of("music").toAbsolutePath();
        when(mediaFolderService.getMusicFoldersForUser(USERNAME))
                .thenReturn(List.of(new MusicFolder(1, folder, "Music", MusicFolder.Type.MEDIA, true, Instant.now())));

        mockMvc.perform(get("/more.view"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("model", hasEntry("uploadDirectory", (Object) (folder + "/Incoming"))));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void leavesTheUploadFolderEmptyForUserWithoutMusicFolders() throws Exception {
        when(mediaFolderService.getMusicFoldersForUser(USERNAME)).thenReturn(List.of());

        mockMvc.perform(get("/more.view"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("model", hasEntry("uploadDirectory", (Object) "")))
                .andExpect(content().string(containsString("name=\"dir\"")));
    }

    @Test
    @WithMockUser(username = USERNAME)
    void leavesTheUploadFolderEmptyForInvalidPattern() throws Exception {
        when(settingsService.getUploadsFolder()).thenReturn("%{[}/Incoming");
        when(mediaFolderService.getMusicFoldersForUser(USERNAME)).thenReturn(List.of());

        mockMvc.perform(get("/more.view"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("model", hasEntry("uploadDirectory", (Object) "")));
    }
}
