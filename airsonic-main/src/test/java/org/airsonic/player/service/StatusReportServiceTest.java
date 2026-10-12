package org.airsonic.player.service;

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.service.StatusReportService.LogEntry;
import org.airsonic.player.service.StatusReportService.Transfer;
import org.airsonic.player.service.StatusReportService.TransferType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatusReportServiceTest {

    @Test
    public void simpleClassNameStripsPackagesLambdasAndInnerClasses() {
        assertEquals("MediaScannerService",
                StatusReportService.simpleClassName("org.airsonic.player.service.MediaScannerService$$Lambda/0x0000012345"));
        assertEquals("PodcastDownloadClient",
                StatusReportService.simpleClassName("org.airsonic.player.service.podcast.PodcastDownloadClient$1"));
        assertEquals("Plain", StatusReportService.simpleClassName("Plain"));
        assertEquals("LibraryCleaner", StatusReportService.simpleClassName("org.example.LibraryCleaner@1a2b3c"));
        assertNull(StatusReportService.simpleClassName(null));
    }

    @Test
    public void transferReportsProgressAndOwner() {
        Player player = new Player();
        player.setId(1);
        player.setUsername("alice");
        player.setType("Web");
        TransferStatus status = new TransferStatus(player);
        status.setBytesTotal(1000);
        status.setBytesSkipped(100);
        status.setBytesTransferred(400);

        Transfer transfer = StatusReportService.toTransfer(status, TransferType.download);

        assertEquals(TransferType.download, transfer.type());
        assertEquals("alice", transfer.username());
        assertEquals("Web", transfer.playerType());
        assertEquals(400, transfer.bytesTransferred());
        assertEquals(0.5, transfer.progress(), 1e-9);
        assertTrue(transfer.active());
        assertEquals(0.0, transfer.currentKbps(), 1e-9);
        assertTrue(transfer.started() > 0);
    }

    @Test
    public void transferWithoutSizeHasNoProgress() {
        TransferStatus status = new TransferStatus(new Player());
        status.setActive(false);

        Transfer transfer = StatusReportService.toTransfer(status, TransferType.stream);

        assertNull(transfer.progress());
        assertFalse(transfer.active());
        assertNull(transfer.currentKbps());
    }

    @Test
    public void logEntriesAreTheWarningsAndErrors() {
        List<LogEntry> entries = StatusReportService.parseLogEntries(List.of(
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
