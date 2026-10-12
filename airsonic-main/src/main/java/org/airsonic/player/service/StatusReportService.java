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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaLibraryStatistics;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.TransferStatus.Sample;
import org.airsonic.player.service.StatusReport.BitratePoint;
import org.airsonic.player.service.StatusReport.Library;
import org.airsonic.player.service.StatusReport.Transfer;
import org.airsonic.player.service.StatusReport.TransferType;
import org.airsonic.player.service.StatusReport.UserTotals;
import org.airsonic.player.service.search.IndexManager;
import org.airsonic.player.util.FileUtil;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Collects everything the status page shows in a single report.
 *
 * Administrators get the whole report. Other users only get their own transfers and the library
 * summary; the sections about the server are left out (null).
 */
@Service
public class StatusReportService {

    /** Recently finished streams are listed for this long. */
    private static final long RECENT_STREAM_MILLIS = 60L * 60L * 1000L;
    /** A transfer with no new sample for this long is idle. */
    private static final long IDLE_MILLIS = 15_000L;
    /** Number of bitrate points sent per transfer (one sample every five seconds). */
    private static final int BITRATE_POINTS = 60;

    private final StatusService statusService;
    private final UserService userService;
    private final IndexManager indexManager;
    private final MediaScannerService mediaScannerService;
    private final ServerMetricsService serverMetricsService;
    private final ServerResourcesService serverResourcesService;
    private final LogSummaryService logSummaryService;

    public StatusReportService(StatusService statusService, UserService userService, IndexManager indexManager,
            MediaScannerService mediaScannerService, ServerMetricsService serverMetricsService,
            ServerResourcesService serverResourcesService, LogSummaryService logSummaryService) {
        this.statusService = statusService;
        this.userService = userService;
        this.indexManager = indexManager;
        this.mediaScannerService = mediaScannerService;
        this.serverMetricsService = serverMetricsService;
        this.serverResourcesService = serverResourcesService;
        this.logSummaryService = logSummaryService;
    }

    /**
     * @param username the user the report is for
     * @param admin    whether that user is an administrator
     */
    public StatusReport getReport(String username, boolean admin) {
        Predicate<TransferStatus> visible = status -> admin || (status.getPlayer() != null
                && Objects.equals(status.getPlayer().getUsername(), username));
        List<Transfer> transfers = getTransfers(visible);
        List<UserTotals> users = userService.getAllUsers().stream()
                .filter(user -> admin || user.getUsername().equals(username))
                .map(user -> new UserTotals(user.getUsername(), user.getBytesStreamed(), user.getBytesDownloaded(),
                        user.getBytesUploaded()))
                .sorted(Comparator.comparingLong(UserTotals::total).reversed().thenComparing(UserTotals::user))
                .toList();
        if (!admin) {
            return new StatusReport(System.currentTimeMillis(), false, getLibrary(), transfers, users,
                    null, null, null, null, null, null, null, null, null, null);
        }
        int activeStreams = (int) transfers.stream()
                .filter(t -> t.active() && t.type() == TransferType.STREAM)
                .count();
        return new StatusReport(
                System.currentTimeMillis(),
                true,
                getLibrary(),
                transfers,
                users,
                ManagementFactory.getRuntimeMXBean().getUptime(),
                serverMetricsService.getSessions(),
                serverMetricsService.getCaches(),
                serverMetricsService.getTasks(),
                serverMetricsService.getPathWatchers(),
                serverResourcesService.getSystem(),
                serverResourcesService.getStorage(),
                serverResourcesService.getPlayback(activeStreams),
                serverResourcesService.getNetwork(),
                logSummaryService.getLog());
    }

    private List<Transfer> getTransfers(Predicate<TransferStatus> visible) {
        Stream<Transfer> streams = statusService.getAllStreamStatuses().stream()
                .filter(status -> status.isActive() || status.getMillisSinceLastUpdate() < RECENT_STREAM_MILLIS)
                .filter(visible)
                .map(status -> toTransfer(status, TransferType.STREAM));
        Stream<Transfer> downloads = statusService.getAllDownloadStatuses().stream()
                .filter(visible)
                .map(status -> toTransfer(status, TransferType.DOWNLOAD));
        Stream<Transfer> uploads = statusService.getAllUploadStatuses().stream()
                .filter(visible)
                .map(status -> toTransfer(status, TransferType.UPLOAD));
        return Stream.of(streams, downloads, uploads).flatMap(s -> s)
                // active transfers first, then the most recent ones
                .sorted(Comparator.comparing(Transfer::active).reversed()
                        .thenComparing(Comparator.comparingLong(Transfer::started).reversed()))
                .toList();
    }

    static Transfer toTransfer(TransferStatus status, TransferType type) {
        Optional<Player> player = Optional.ofNullable(status.getPlayer());
        String path = Optional.ofNullable(status.getMediaFile())
                .map(MediaFile::getRelativePath)
                .or(() -> Optional.ofNullable(status.getExternalFile()))
                .map(FileUtil::getShortPath)
                .orElse(null);

        long idleMillis = status.getMillisSinceLastUpdate();
        boolean active = status.isActive();
        List<BitratePoint> bitrate = toBitrate(status);
        Double currentKbps = null;
        if (active) {
            currentKbps = idleMillis > IDLE_MILLIS || bitrate.isEmpty() ? 0.0 : bitrate.get(bitrate.size() - 1).kbps();
        }

        long total = status.getBytesTotal();
        Double progress = total > 0
                ? Math.min(1.0, (double) (status.getBytesTransferred() + status.getBytesSkipped()) / total)
                : null;

        return new Transfer(
                status.getId().toString(),
                type,
                active,
                player.map(Player::toString).orElse(null),
                player.map(Player::getType).orElse(null),
                player.map(Player::getUsername).orElse(null),
                path,
                status.getBytesTransferred(),
                total,
                progress,
                status.getStarted(),
                idleMillis,
                currentKbps,
                bitrate);
    }

    /** The bitrate between consecutive samples of the transfer, at most the last {@link #BITRATE_POINTS}. */
    private static List<BitratePoint> toBitrate(TransferStatus status) {
        List<BitratePoint> bitrate = new ArrayList<>();
        Sample previous = null;
        for (Sample sample : status.getHistory()) {
            if (previous != null && sample.getTimestamp() > previous.getTimestamp()) {
                long elapsed = sample.getTimestamp() - previous.getTimestamp();
                long bytes = Math.max(0, sample.getBytesTransferred() - previous.getBytesTransferred());
                bitrate.add(new BitratePoint(sample.getTimestamp(), (8.0 * bytes / 1024.0) / (elapsed / 1000.0)));
            }
            previous = sample;
        }
        return bitrate.size() > BITRATE_POINTS
                ? new ArrayList<>(bitrate.subList(bitrate.size() - BITRATE_POINTS, bitrate.size()))
                : bitrate;
    }

    private Library getLibrary() {
        MediaLibraryStatistics statistics = indexManager.getStatistics();
        boolean scanning = mediaScannerService.isScanning();
        int scanCount = scanning ? mediaScannerService.getScanCount() : 0;
        if (statistics == null) {
            return new Library(null, null, null, null, null, null, scanning, scanCount);
        }
        return new Library(statistics.getArtistCount(), statistics.getAlbumCount(), statistics.getSongCount(),
                statistics.getTotalLengthInBytes(), (long) statistics.getTotalDurationInSeconds(),
                Optional.ofNullable(statistics.getScanDate()).map(Instant::toEpochMilli).orElse(null),
                scanning, scanCount);
    }
}
