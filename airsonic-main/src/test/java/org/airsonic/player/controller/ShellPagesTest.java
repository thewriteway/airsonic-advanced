package org.airsonic.player.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellPagesTest {

    @Test
    void isPageAcceptsBothMappingStyles() {
        assertTrue(ShellPages.isPage("/home"));
        assertTrue(ShellPages.isPage("/home.view"));
        assertTrue(ShellPages.isPage("main.view?id=3"));
    }

    @Test
    void isPageRejectsEverythingElse() {
        assertFalse(ShellPages.isPage("/index"));
        assertFalse(ShellPages.isPage("/left"));
        assertFalse(ShellPages.isPage("/download.view?id=3"));
        assertFalse(ShellPages.isPage("/rest/ping.view"));
        assertFalse(ShellPages.isPage("//evil.example/home"));
        assertFalse(ShellPages.isPage("https://evil.example/home"));
    }

    @Test
    void toMainUrlKeepsPagesAndQuery() {
        assertEquals("main.view?id=3", ShellPages.toMainUrl("/main.view?id=3"));
        assertEquals("personalSettings.view", ShellPages.toMainUrl("personalSettings.view"));
    }

    @Test
    void toMainUrlFallsBackToDefault() {
        assertEquals(ShellPages.DEFAULT_MAIN, ShellPages.toMainUrl(null));
        assertEquals(ShellPages.DEFAULT_MAIN, ShellPages.toMainUrl("javascript:alert(1)"));
        assertEquals(ShellPages.DEFAULT_MAIN, ShellPages.toMainUrl("//evil.example/home"));
        assertEquals(ShellPages.DEFAULT_MAIN, ShellPages.toMainUrl("download.view?id=3"));
    }
}
