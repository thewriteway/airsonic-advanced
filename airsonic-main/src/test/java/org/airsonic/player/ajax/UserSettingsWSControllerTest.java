package org.airsonic.player.ajax;

import org.airsonic.player.domain.Theme;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsWSControllerTest {

    private static final Principal USER = () -> "testuser";

    @Mock
    private PersonalSettingsService personalSettingsService;

    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private UserSettingsWSController controller;

    @BeforeEach
    void setUp() {
        when(settingsService.getAvailableThemes()).thenReturn(new Theme[] {new Theme("default", "Light"), new Theme("dark", "Dark")});
    }

    @Test
    void savesAnAvailableTheme() {
        assertEquals("dark", controller.setTheme(USER, "dark"));

        verify(personalSettingsService).updateTheme("testuser", "dark");
    }

    @Test
    void rejectsAnUnknownTheme() {
        assertThrows(IllegalArgumentException.class, () -> controller.setTheme(USER, "no-such-theme"));

        verify(personalSettingsService, never()).updateTheme(any(), any());
    }
}
