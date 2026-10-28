package org.airsonic.player.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerMetricsServiceTest {

    @Test
    void simpleClassNameStripsPackagesLambdasAndInnerClasses() {
        assertEquals("MediaScannerService",
                ServerMetricsService.simpleClassName("org.airsonic.player.service.MediaScannerService$$Lambda/0x0000012345"));
        assertEquals("PodcastDownloadClient",
                ServerMetricsService.simpleClassName("org.airsonic.player.service.podcast.PodcastDownloadClient$1"));
        assertEquals("Plain", ServerMetricsService.simpleClassName("Plain"));
        assertEquals("LibraryCleaner", ServerMetricsService.simpleClassName("org.example.LibraryCleaner@1a2b3c"));
        assertNull(ServerMetricsService.simpleClassName(null));
    }
}
