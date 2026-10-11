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
package org.airsonic.player.api;

import org.airsonic.player.controller.CaptionsController;
import org.airsonic.player.controller.CaptionsController.CaptionInfo;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.metadata.MetaData;
import org.airsonic.player.service.metadata.Track;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the /rest/getVideoInfo and /rest/getCaptions endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class VideoApiTest {

    private static final String AIRSONIC_USER = "admin";
    private static final String AIRSONIC_PASSWORD = "admin";
    private static final String AIRSONIC_API_VERSION = "1.15.0";
    private static final int VIDEO_ID = 4242;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CaptionsController captionsController;

    @MockitoSpyBean
    private MediaFileService mediaFileService;

    @MockitoSpyBean
    private SecurityService securityService;

    @TempDir
    private static Path tempAirsonicHome;

    private final MediaFile video = new MediaFile();

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempAirsonicHome.toString());
    }

    @BeforeEach
    public void setup() {
        if (!securityService.checkDefaultAdminCredsPresent()) {
            securityService.recoverCredential(AIRSONIC_USER, AIRSONIC_PASSWORD, "Test admin credential");
        }
        video.setId(VIDEO_ID);
        video.setMediaType(MediaFile.MediaType.VIDEO);
        doReturn(video).when(mediaFileService).getMediaFile(VIDEO_ID);
        doReturn(true).when(securityService).isFolderAccessAllowed(eq(video), anyString());
    }

    private MockHttpServletRequestBuilder restRequest(String endpoint) {
        return get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", "test")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", "json");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getVideoInfo", "/rest/getVideoInfo.view" })
    public void testGetVideoInfoListsCaptionsAndAudioTracks(String endpoint) throws Exception {
        when(captionsController.listCaptions(eq(video), anyString())).thenReturn(List.of(
                new CaptionInfo("2", CaptionInfo.Location.internal, "srt", "eng", "url1"),
                new CaptionInfo("/videos/movie.fr.vtt", CaptionInfo.Location.external, "vtt", "movie.fr.vtt", "url2")));
        MetaData metaData = new MetaData();
        metaData.addTrack(new Track(0, "video", null, "h264"));
        metaData.addTrack(new Track(1, "audio", "eng", "aac"));
        when(captionsController.getVideoMetaData(video)).thenReturn(metaData);

        mvc.perform(restRequest(endpoint).param("id", String.valueOf(VIDEO_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.videoInfo.id").value(String.valueOf(VIDEO_ID)))
                // caption ids are list positions, never the external file path
                .andExpect(jsonPath("$.subsonic-response.videoInfo.captions[0].id").value("0"))
                .andExpect(jsonPath("$.subsonic-response.videoInfo.captions[0].name").value("eng"))
                .andExpect(jsonPath("$.subsonic-response.videoInfo.captions[1].id").value("1"))
                .andExpect(jsonPath("$.subsonic-response.videoInfo.captions[1].name").value("movie.fr.vtt"))
                .andExpect(jsonPath("$.subsonic-response.videoInfo.audioTrack.length()").value(1))
                .andExpect(jsonPath("$.subsonic-response.videoInfo.audioTrack[0].id").value("1"))
                .andExpect(jsonPath("$.subsonic-response.videoInfo.audioTrack[0].name").value("English"))
                .andExpect(jsonPath("$.subsonic-response.videoInfo.audioTrack[0].languageCode").value("eng"));
    }

    @Test
    public void testGetVideoInfoRejectsNonVideo() throws Exception {
        video.setMediaType(MediaFile.MediaType.MUSIC);

        mvc.perform(restRequest("/rest/getVideoInfo").param("id", String.valueOf(VIDEO_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(70));
    }

    @Test
    public void testGetVideoInfoRequiresFolderAccess() throws Exception {
        doReturn(false).when(securityService).isFolderAccessAllowed(eq(video), anyString());

        mvc.perform(restRequest("/rest/getVideoInfo").param("id", String.valueOf(VIDEO_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(50));
        verify(captionsController, never()).listCaptions(any(MediaFile.class), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getCaptions", "/rest/getCaptions.view" })
    public void testGetCaptionsReturnsCaptionFile(String endpoint) throws Exception {
        byte[] vtt = "WEBVTT\n\n00:00.000 --> 00:01.000\nHello".getBytes(StandardCharsets.UTF_8);
        when(captionsController.handleRequest(any(), eq(VIDEO_ID), isNull(), eq("vtt"), any()))
                .thenReturn(ResponseEntity.ok(new ByteArrayResource(vtt)));

        mvc.perform(restRequest(endpoint).param("id", String.valueOf(VIDEO_ID)).param("format", "vtt"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(vtt));
    }

    @Test
    public void testGetCaptionsReportsMissingCaptions() throws Exception {
        when(captionsController.handleRequest(any(), eq(VIDEO_ID), isNull(), isNull(), any()))
                .thenThrow(new NotFoundException("No captions found for file id: " + VIDEO_ID));

        mvc.perform(restRequest("/rest/getCaptions").param("id", String.valueOf(VIDEO_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(70));
    }
}
