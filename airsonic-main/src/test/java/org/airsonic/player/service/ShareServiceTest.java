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
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.entity.Share;
import org.airsonic.player.domain.entity.ShareFile;
import org.airsonic.player.repository.ShareRepository;
import org.airsonic.player.util.NetworkUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ShareServiceTest {

    @Mock
    private ShareRepository shareRepository;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private JWTSecurityService jwtSecurityService;

    @InjectMocks
    private ShareService shareService;

    @Mock
    private User mockedUser;
    @Mock
    private HttpServletRequest mockedRequest;
    @Mock
    private Share mockedShare;

    @Test
    public void testGetSharesForUserWithAdminUserShouldReturnsAllShares() {

        // given
        List<Share> allShares = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Share share = new Share("share" + i, "description", "user" + i, Instant.now(), Instant.now(), Instant.now(), i);
            share.setId(i);
            allShares.add(share);
        }
        when(mockedUser.isAdminRole()).thenReturn(true);
        when(shareRepository.findAll()).thenReturn(allShares);

        // when
        List<Share> shares = shareService.getSharesForUser(mockedUser);

        // then
        assertEquals(allShares, shares);
        verify(mockedUser, never()).getUsername();
    }

    @Test
    public void testGetSharesForUserWithNormalUserShouldReturnsUserShares() {
        // given
        List<Share> allShares = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Share share = new Share("share" + i, "description", "user" + i, Instant.now(), Instant.now(), Instant.now(), i);
            share.setId(i);
            allShares.add(share);
        }
        when(mockedUser.isAdminRole()).thenReturn(false);
        when(mockedUser.getUsername()).thenReturn("user1");
        List<Share> expectedShares = Arrays.asList(allShares.get(1));
        when(shareRepository.findByUsername("user1")).thenReturn(expectedShares);

        // when
        List<Share> shares = shareService.getSharesForUser(mockedUser);

        // then
        assertEquals(expectedShares, shares);
        verify(shareRepository, never()).findAll();
    }

    @Test
    public void testCreateShare() {

        // given
        List<MediaFile> files = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MediaFile file = new MediaFile();
            file.setId(i);
            files.add(file);
        }
        String currentUsername = "testuser";

        doAnswer(invocation -> {
            Share share = invocation.getArgument(0);
            share.setId(1);
            return share;
        }).when(shareRepository).save(any(Share.class));

        // テスト対象のメソッドを呼び出します
        Share result = shareService.createShare(currentUsername, files);

        // 結果を検証します
        verify(shareRepository).save(any(Share.class));
        assertEquals(1, result.getId());
        assertEquals(currentUsername, result.getUsername());
        assertEquals(result.getCreated().plus(ChronoUnit.YEARS.getDuration()), result.getExpires());
        assertEquals(5, result.getName().length());
        assertEquals(result.getFiles().size(), files.size());
    }

    @Test
    public void testGetSharedFiles() {
        // given
        int shareId = 1;
        List<MusicFolder> musicFolders = new ArrayList<>();
        MusicFolder folder = new MusicFolder(3, null, null, null, false, null);
        musicFolders.add(folder);
        List<MediaFile> files = new ArrayList<>();
        Share share = new Share();
        share.setId(shareId);
        for (int i = 0; i < 10; i++) {
            MediaFile file = new MediaFile();
            file.setId(i);
            file.setFolder(folder);
            file.setPresent(true);
            files.add(file);
            when(mediaFileService.getMediaFile(i)).thenReturn(file);
            share.getFiles().add(new ShareFile(share, i));
        }
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        // when
        List<MediaFile> result = shareService.getSharedFiles(shareId, musicFolders);

        // then
        assertEquals(files.size(), result.size());
        verify(mediaFileService, times(files.size())).getMediaFile(anyInt());
    }

    @Test
    public void testGetShareUrl() {
        // given
        String shareName = "abcd1234";
        Instant expires = Instant.now().plusSeconds(3600); // 1 hour from now
        when(mockedShare.getName()).thenReturn(shareName);
        when(mockedShare.getExpires()).thenReturn(expires);
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString("/ext/share/" + shareName);
        when(jwtSecurityService.addJWTToken(eq(User.USERNAME_ANONYMOUS), any(UriComponentsBuilder.class), eq(expires))).thenReturn(uriComponentsBuilder);

        try (MockedStatic<NetworkUtil> mockedNetworkUtil = Mockito.mockStatic(NetworkUtil.class)) {
            mockedNetworkUtil.when(() -> NetworkUtil.getBaseUrl(mockedRequest)).thenReturn("http://example.com");

            // when
            String result = shareService.getShareUrl(mockedRequest, mockedShare);

            // then
            assertEquals("http://example.com/ext/share/" + shareName, result);
        }
    }
}
