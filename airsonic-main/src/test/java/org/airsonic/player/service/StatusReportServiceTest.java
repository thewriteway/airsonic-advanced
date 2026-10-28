package org.airsonic.player.service;

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.service.StatusReport.Transfer;
import org.airsonic.player.service.StatusReport.TransferType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusReportServiceTest {

    @Test
    void transferReportsProgressAndOwner() {
        Player player = new Player();
        player.setId(1);
        player.setUsername("alice");
        player.setType("Web");
        TransferStatus status = new TransferStatus(player);
        status.setBytesTotal(1000);
        status.setBytesSkipped(100);
        status.setBytesTransferred(400);

        Transfer transfer = StatusReportService.toTransfer(status, TransferType.DOWNLOAD);

        assertEquals(TransferType.DOWNLOAD, transfer.type());
        assertEquals("alice", transfer.username());
        assertEquals("Web", transfer.playerType());
        assertEquals(400, transfer.bytesTransferred());
        assertEquals(0.5, transfer.progress(), 1e-9);
        assertTrue(transfer.active());
        assertEquals(0.0, transfer.currentKbps(), 1e-9);
        assertTrue(transfer.started() > 0);
    }

    @Test
    void transferWithoutSizeHasNoProgress() {
        TransferStatus status = new TransferStatus(new Player());
        status.setActive(false);

        Transfer transfer = StatusReportService.toTransfer(status, TransferType.STREAM);

        assertNull(transfer.progress());
        assertFalse(transfer.active());
        assertNull(transfer.currentKbps());
    }
}
