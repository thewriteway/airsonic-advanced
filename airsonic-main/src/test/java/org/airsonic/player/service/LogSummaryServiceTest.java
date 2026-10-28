package org.airsonic.player.service;

import org.airsonic.player.service.StatusReport.LogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSummaryServiceTest {

    @Test
    void logEntriesAreTheWarningsAndErrors() {
        List<LogEntry> entries = LogSummaryService.parseLogEntries(List.of(
                "2026-10-12 10:57:28.764  INFO --- org.airsonic.player.Application          : Started Application",
                "2026-10-12 10:58:01.002 ERROR --- o.a.p.service.MediaScannerService        : Scan failed: disk gone",
                "java.io.IOException: disk gone",
                "\tat org.airsonic.player.service.MediaScannerService.scan(MediaScannerService.java:42)",
                "2026-10-12 10:57:27.226  WARN --- r$InitializeUserDetailsManagerConfigurer : Global AuthenticationManager : configured"));

        assertEquals(2, entries.size());
        assertEquals("ERROR", entries.get(0).level());
        assertEquals("o.a.p.service.MediaScannerService", entries.get(0).logger());
        assertEquals("Scan failed: disk gone", entries.get(0).message());
        assertTrue(entries.get(0).time() > 0);
        assertEquals("WARN", entries.get(1).level());
        assertEquals("r$InitializeUserDetailsManagerConfigurer", entries.get(1).logger());
        assertEquals("Global AuthenticationManager : configured", entries.get(1).message());
    }
}
