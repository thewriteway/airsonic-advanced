package org.airsonic.player.ajax;

import org.airsonic.player.ajax.LyricsWSController.LyricsGetRequest;
import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LyricsWSControllerTest {

    @Mock
    private LyricsService lyricsService;
    @Mock
    private SecurityService securityService;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private Principal user;
    @Mock
    private MediaFile mediaFile;

    @InjectMocks
    private LyricsWSController controller;

    @BeforeEach
    void setUp() {
        lenient().when(user.getName()).thenReturn("user");
    }

    @Test
    void reportsLocalLyricsAsFound() {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "user")).thenReturn(true);
        when(lyricsService.getLyricsFromMediaFile(mediaFile)).thenReturn(new Lyrics("la la", 1, "file"));

        LyricsStatus status = controller.getLyrics(user, new LyricsGetRequest("Artist", "Song", 1));

        assertTrue(status.isPersisted());
        assertFalse(status.isTryLater());
    }

    @Test
    void reportsNoLyricsWhenNoneFound() {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "user")).thenReturn(true);

        LyricsStatus status = controller.getLyrics(user, new LyricsGetRequest("Artist", "Song", 1));

        assertFalse(status.isPersisted());
        assertFalse(status.isTryLater());
    }

    @Test
    void doesNotLookUpLyricsOutsideAllowedFolders() {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "user")).thenReturn(false);

        LyricsStatus status = controller.getLyrics(user, new LyricsGetRequest("Artist", "Song", 1));

        assertFalse(status.isPersisted());
        verify(lyricsService, never()).getLyricsFromMediaFile(any());
    }

    @Test
    void ignoresRequestWithoutId() {
        LyricsStatus status = controller.getLyrics(user, new LyricsGetRequest("Artist", "Song", null));

        assertFalse(status.isPersisted());
        verify(mediaFileService, never()).getMediaFile(any(Integer.class));
    }
}
