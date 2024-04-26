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

 Copyright 2023-2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Avatar;
import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.entity.CustomAvatar;
import org.airsonic.player.domain.entity.SystemAvatar;
import org.airsonic.player.domain.entity.UserSetting;
import org.airsonic.player.domain.entity.UserSettingDetail;
import org.airsonic.player.repository.CustomAvatarRepository;
import org.airsonic.player.repository.SystemAvatarRepository;
import org.airsonic.player.repository.UserSettingRepository;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PersonalSettingsServiceTest {

    @InjectMocks
    private PersonalSettingsService service;

    @Mock
    private SystemAvatarRepository systemAvatarRepository;

    @Mock
    private CustomAvatarRepository customAvatarRepository;

    @Mock
    private AirsonicHomeConfig homeConfig;

    @Mock
    private UserSettingRepository userSettingRepository;

    @Mock
    private UserSetting mockedUserSetting;

    @Mock
    private UserSettingDetail mockedUserSettingDetail;

    @TempDir
    private Path tempDir;

    @Test
    public void testGetSystemAvatars() throws Exception {
        SystemAvatar avatar = new SystemAvatar(10, "avatar", Instant.now(), "image/png", 60, 70,
                Paths.get("icons/avatars/avatar.png"));
        when(systemAvatarRepository.findAll()).thenReturn(new ArrayList<>(List.of(avatar)));

        Avatar actual = service.getSystemAvatars().get(0);
        assertEquals(avatar.getId(), actual.getId());
        assertEquals(avatar.getName(), actual.getName());
        assertEquals(avatar.getMimeType(), actual.getMimeType());
        assertEquals(avatar.getWidth(), actual.getWidth());
        assertEquals(avatar.getHeight(), actual.getHeight());
        assertEquals(avatar.getPath(), actual.getPath());
        assertEquals(avatar.getCreatedDate(), actual.getCreatedDate());
    }

    @Test
    public void testGetSystemAvatar() throws Exception {
        SystemAvatar avatar = new SystemAvatar(10, "avatar", Instant.now(), "image/png", 60, 70,
                Paths.get("icons/avatars/avatar.png"));
        when(systemAvatarRepository.findById(10)).thenReturn(Optional.of(avatar));

        Avatar actual = service.getSystemAvatar(10);
        assertEquals(avatar.getId(), actual.getId());
        assertEquals(avatar.getName(), actual.getName());
        assertEquals(avatar.getMimeType(), actual.getMimeType());
        assertEquals(avatar.getWidth(), actual.getWidth());
        assertEquals(avatar.getHeight(), actual.getHeight());
        assertEquals(avatar.getPath(), actual.getPath());
        assertEquals(avatar.getCreatedDate(), actual.getCreatedDate());
    }

    @Test
    public void testGetSystemAvatarReturnNull() throws Exception {
        when(systemAvatarRepository.findById(10)).thenReturn(Optional.empty());

        Avatar actual = service.getSystemAvatar(10);
        assertEquals(null, actual);
    }

    @Test
    public void testGetSystemAvatarWhenIdIsNull() throws Exception {
        Avatar actual = service.getSystemAvatar(null);
        assertEquals(null, actual);
        verify(systemAvatarRepository, never()).findById(anyInt());
    }

    @Test
    public void testGetCustomAvatar() throws Exception {
        CustomAvatar avatar = new CustomAvatar(10, "avatar", Instant.now(), "image/png", 60, 70,
                Paths.get("$[AIRSONIC_HOME]/icons/avatars/avatar.png"), "user");
        when(customAvatarRepository.findByUsername("user")).thenReturn(Optional.of(avatar));
        when(homeConfig.getAirsonicHome()).thenReturn(Paths.get("/airsonic_home"));

        Avatar actual = service.getCustomAvatar("user");
        assertEquals(avatar.getId(), actual.getId());
        assertEquals(avatar.getName(), actual.getName());
        assertEquals(avatar.getMimeType(), actual.getMimeType());
        assertEquals(avatar.getWidth(), actual.getWidth());
        assertEquals(avatar.getHeight(), actual.getHeight());
        assertEquals(Paths.get("/airsonic_home/icons/avatars/avatar.png"), actual.getPath());
        assertEquals(avatar.getCreatedDate(), actual.getCreatedDate());
    }

    @Test
    public void testGetCustomAvatarReturnNull() throws Exception {
        when(customAvatarRepository.findByUsername("user")).thenReturn(Optional.empty());

        Avatar actual = service.getCustomAvatar("user");
        assertEquals(null, actual);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " " })
    public void testGetCustomAvatarWhenUsernameIsNull(String username) throws Exception {
        Avatar actual = service.getCustomAvatar(username);
        assertEquals(null, actual);
        verify(customAvatarRepository, never()).findByUsername(username);
    }

    @Test
    public void testGetAvatarWithIdReturnSystemAvatar() throws Exception {
        SystemAvatar avatar = new SystemAvatar(10, "avatar", Instant.now(), "image/png", 60, 70,
                Paths.get("icons/avatars/avatar.png"));
        when(systemAvatarRepository.findById(10)).thenReturn(Optional.of(avatar));

        Avatar actual = service.getAvatar(10, null, false);
        assertEquals(avatar.getId(), actual.getId());
        assertEquals(avatar.getName(), actual.getName());
        assertEquals(avatar.getMimeType(), actual.getMimeType());
        assertEquals(avatar.getWidth(), actual.getWidth());
        assertEquals(avatar.getHeight(), actual.getHeight());
        assertEquals(avatar.getPath(), actual.getPath());
        assertEquals(avatar.getCreatedDate(), actual.getCreatedDate());
        verifyNoInteractions(customAvatarRepository);
        verifyNoInteractions(userSettingRepository);
    }

    @Test
    public void testGetAvatarWithUsernameReturnCustomAvatar() throws Exception {
        CustomAvatar avatar = new CustomAvatar(10, "avatar", Instant.now(), "image/png", 60, 70,
                Paths.get("$[AIRSONIC_HOME]/icons/avatars/avatar.png"), "user");
        when(customAvatarRepository.findByUsername("user")).thenReturn(Optional.of(avatar));
        when(homeConfig.getAirsonicHome()).thenReturn(Paths.get("/airsonic_home"));
        when(userSettingRepository.findByUsername("user")).thenReturn(Optional.of(mockedUserSetting));
        when(mockedUserSetting.getSettings()).thenReturn(mockedUserSettingDetail);
        when(mockedUserSettingDetail.getAvatarScheme()).thenReturn(AvatarScheme.CUSTOM);

        Avatar actual = service.getAvatar(null, "user", false);
        assertEquals(avatar.getId(), actual.getId());
        assertEquals(avatar.getName(), actual.getName());
        assertEquals(avatar.getMimeType(), actual.getMimeType());
        assertEquals(avatar.getWidth(), actual.getWidth());
        assertEquals(avatar.getHeight(), actual.getHeight());
        assertEquals(Paths.get("/airsonic_home/icons/avatars/avatar.png"), actual.getPath());
        assertEquals(avatar.getCreatedDate(), actual.getCreatedDate());
        verifyNoInteractions(systemAvatarRepository);
    }

    @Test
    public void testGetAvatarWithForceCustom() throws Exception {
        CustomAvatar avatar = new CustomAvatar(10, "avatar", Instant.now(), "image/png", 60, 70,
                Paths.get("$[AIRSONIC_HOME]/icons/avatars/avatar.png"), "user");
        when(customAvatarRepository.findByUsername("user")).thenReturn(Optional.of(avatar));
        when(homeConfig.getAirsonicHome()).thenReturn(Paths.get("/airsonic_home"));
        when(userSettingRepository.findByUsername("user")).thenReturn(Optional.of(mockedUserSetting));

        Avatar actual = service.getAvatar(null, "user", true);
        assertEquals(avatar.getId(), actual.getId());
        assertEquals(avatar.getName(), actual.getName());
        assertEquals(avatar.getMimeType(), actual.getMimeType());
        assertEquals(avatar.getWidth(), actual.getWidth());
        assertEquals(avatar.getHeight(), actual.getHeight());
        assertEquals(Paths.get("/airsonic_home/icons/avatars/avatar.png"), actual.getPath());
        assertEquals(avatar.getCreatedDate(), actual.getCreatedDate());
        verifyNoInteractions(systemAvatarRepository);
        verifyNoInteractions(mockedUserSetting);
    }

    @Test
    public void testGetAvatarWhenSchemeIsNoneReturnNull() throws Exception {

        // given
        when(userSettingRepository.findByUsername("user")).thenReturn(Optional.of(mockedUserSetting));
        when(mockedUserSetting.getSettings()).thenReturn(mockedUserSettingDetail);
        when(mockedUserSettingDetail.getAvatarScheme()).thenReturn(AvatarScheme.NONE);

        // when
        Avatar actual = service.getAvatar(null, "user", false);

        // then
        assertNull(actual);
        verifyNoInteractions(systemAvatarRepository);
        verifyNoInteractions(customAvatarRepository);
    }

    @Test
    public void testGetAvatarWhenSchemeIsSystemReturnSystemAvatar() throws Exception {
        SystemAvatar avatar = new SystemAvatar(10, "avatar", Instant.now(), "image/png", 60, 70,
                Paths.get("icons/avatars/avatar.png"));
        when(systemAvatarRepository.findById(10)).thenReturn(Optional.of(avatar));
        when(userSettingRepository.findByUsername("user")).thenReturn(Optional.of(mockedUserSetting));
        when(mockedUserSetting.getSettings()).thenReturn(mockedUserSettingDetail);
        when(mockedUserSettingDetail.getAvatarScheme()).thenReturn(AvatarScheme.SYSTEM);
        when(mockedUserSettingDetail.getSystemAvatarId()).thenReturn(10);

        Avatar actual = service.getAvatar(null, "user", false);
        assertEquals(avatar.getId(), actual.getId());
        assertEquals(avatar.getName(), actual.getName());
        assertEquals(avatar.getMimeType(), actual.getMimeType());
        assertEquals(avatar.getWidth(), actual.getWidth());
        assertEquals(avatar.getHeight(), actual.getHeight());
        assertEquals(avatar.getPath(), actual.getPath());
        assertEquals(avatar.getCreatedDate(), actual.getCreatedDate());
        verifyNoInteractions(customAvatarRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = { "small.png", "small.jpg", "small.gif", "small.bmp" })
    public void testCreateCustomAvatarWithSmallImage(String fileName) throws Exception {

        // given
        ArgumentCaptor<CustomAvatar> customAvatarCaptor = ArgumentCaptor.forClass(CustomAvatar.class);
        ClassPathResource resource = new ClassPathResource(Paths.get("avatars", fileName).toString());
        String USER_NAME = "avatarTestUser";
        byte[] data = resource.getInputStream().readAllBytes();
        when(homeConfig.getAirsonicHome()).thenReturn(tempDir);

        // when
        Map<String, Object> result = service.createCustomAvatar(fileName, data, USER_NAME);

        // then
        verify(customAvatarRepository).deleteAllByUsername(USER_NAME);
        verify(customAvatarRepository).save(customAvatarCaptor.capture());

        // assert saved custom avatar
        CustomAvatar actual = customAvatarCaptor.getValue();
        assertEquals(USER_NAME, actual.getUsername());
        assertEquals(fileName, actual.getName());
        assertEquals(50, actual.getWidth());
        assertEquals(50, actual.getHeight());
        String mimeType = StringUtil.getMimeType(FilenameUtils.getExtension(fileName));
        assertEquals(mimeType, actual.getMimeType());
        Path persistedPath = (Paths.get("$[AIRSONIC_HOME]", "avatars", USER_NAME,
                fileName + "." + StringUtils.substringAfter(mimeType, "/")));
        assertEquals(persistedPath, actual.getPath());
        assertNotNull(actual.getCreatedDate());

        // assert saved file
        Path savedPath = tempDir
                .resolve(Paths.get("avatars", USER_NAME, fileName + "." + StringUtils.substringAfter(mimeType, "/")));
        assertTrue(Files.exists(savedPath));
        assertTrue(result.isEmpty());

    }

    @ParameterizedTest
    @ValueSource(strings = { "large.png", "large.jpg", "large.gif", "large.bmp" })
    public void testCreateCustomAvatarWithLargeImage(String fileName) throws Exception {

        // given
        ArgumentCaptor<CustomAvatar> customAvatarCaptor = ArgumentCaptor.forClass(CustomAvatar.class);
        ClassPathResource resource = new ClassPathResource(Paths.get("avatars", fileName).toString());
        String USER_NAME = "avatarTestUser";
        byte[] data = resource.getInputStream().readAllBytes();
        when(homeConfig.getAirsonicHome()).thenReturn(tempDir);

        // when
        Map<String, Object> result = service.createCustomAvatar(fileName, data, USER_NAME);

        // then
        verify(customAvatarRepository).deleteAllByUsername(USER_NAME);
        verify(customAvatarRepository).save(customAvatarCaptor.capture());

        // assert saved custom avatar
        CustomAvatar actual = customAvatarCaptor.getValue();
        assertEquals(USER_NAME, actual.getUsername());
        assertEquals(fileName, actual.getName());
        assertEquals(64, actual.getWidth());
        assertEquals(64, actual.getHeight());
        String mimeType = StringUtil.getMimeType("jpeg");
        assertEquals(mimeType, actual.getMimeType());
        Path persistedPath = (Paths.get("$[AIRSONIC_HOME]", "avatars", USER_NAME, fileName + ".jpeg"));
        assertEquals(persistedPath, actual.getPath());
        assertNotNull(actual.getCreatedDate());

        // assert saved file
        Path savedPath = tempDir.resolve(Paths.get("avatars", USER_NAME, fileName + ".jpeg"));
        assertTrue(Files.exists(savedPath));
        assertTrue((boolean) result.get("resized"));
    }

    @Test
    public void testCreateCustomAvatarWithExceptionReturnError() {
        // given
        String USER_NAME = "avatarTestUser";
        byte[] data = new byte[0];

        // when
        Map<String, Object> result = service.createCustomAvatar("large.png", data, USER_NAME);

        // then
        assertTrue(result.containsKey("error"));
        assertTrue(result.get("error") instanceof IOException);
        verify(customAvatarRepository, never()).deleteAllByUsername(anyString());
        verify(customAvatarRepository, never()).save(any());
        verify(homeConfig, never()).getAirsonicHome();
    }

}