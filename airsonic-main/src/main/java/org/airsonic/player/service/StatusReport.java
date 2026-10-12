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

import java.util.List;

/**
 * Everything shown on the status page. Sections that are only for administrators are null for other users.
 * Times are milliseconds since the epoch.
 */
public record StatusReport(long generated, boolean admin, Library library, List<Transfer> transfers,
        List<UserTotals> users, Long uptimeMillis, Sessions sessions, List<CacheStats> caches,
        List<TaskInfo> tasks, List<WatchedPath> pathWatchers, SystemInfo system, List<StorageInfo> storage,
        PlaybackInfo playback, NetworkInfo network, LogInfo log) {

    /** Marks a run time that cannot be calculated, e.g. the next run of a fixed delay task. */
    public static final long UNKNOWN_TIME = -1L;

    public enum TransferType {
        STREAM, DOWNLOAD, UPLOAD
    }

    /**
     * A transfer. Rates are kbit/s.
     *
     * @param progress      the transferred share of the file between 0 and 1, or null if the size is unknown
     * @param currentKbps   the current bitrate, or null if the transfer has finished
     * @param bitrate       the bitrate history, oldest first
     */
    public record Transfer(String id, TransferType type, boolean active, String player, String playerType,
            String username, String path, long bytesTransferred, long bytesTotal, Double progress, long started,
            long idleMillis, Double currentKbps, List<BitratePoint> bitrate) {
    }

    public record BitratePoint(long time, double kbps) {
    }

    public record UserTotals(String user, long streamed, long downloaded, long uploaded) {
        public long total() {
            return streamed + downloaded + uploaded;
        }
    }

    /**
     * @param lastScan     when the library was last scanned, or null if it never was
     * @param scanCount    the files scanned so far while a scan is running
     */
    public record Library(Integer artists, Integer albums, Integer songs, Long bytes, Long durationSeconds,
            Long lastScan, boolean scanning, int scanCount) {
    }

    public record Sessions(Long current, Long created, Long expired, Long rejected) {
    }

    /**
     * @param hitRatio the share of reads served by the cache between 0 and 1, or null without reads
     */
    public record CacheStats(String name, Double hitRatio, Long hits, Long misses, Long puts, Long removals,
            Long size) {
    }

    /**
     * Run times are null if the task does not run then and {@link StatusReport#UNKNOWN_TIME} if the time cannot be
     * calculated.
     *
     * @param lastStatus the outcome of the last run: STARTED (still running), SUCCESS or ERROR; null if unknown
     * @param lastError  the error of the last run if it failed
     */
    public record TaskInfo(String name, String type, Long created, Long firstRun, Long lastRun, String lastStatus,
            String lastError, Long nextRun, String scheduledBy) {
    }

    public record WatchedPath(String name, String path) {
    }

    /**
     * @param startTime  when the server started
     * @param heapMax    the maximum heap size, or the committed size if there is no maximum
     * @param processCpu the CPU load of Airsonic between 0 and 1, null if unknown
     * @param systemCpu  the CPU load of the whole system between 0 and 1, null if unknown
     */
    public record SystemInfo(String version, Long buildDate, String buildNumber, String java, String os,
            String server, String database, long startTime, long heapUsed, long heapMax, Double processCpu,
            Double systemCpu, int processors, int threads) {
    }

    public enum StorageKind {
        /** The Airsonic home directory, with the database, the index and the logs. */
        HOME,
        MUSIC
    }

    /**
     * @param freeBytes  the space available to Airsonic, null if unknown
     */
    public record StorageInfo(String name, String path, StorageKind kind, boolean enabled, boolean exists,
            Long freeBytes, Long totalBytes) {
    }

    /**
     * @param transcoder the ffmpeg executable that is used, or null if none was found
     */
    public record PlaybackInfo(int activePlayers, int activeStreams, String transcoder, String transcodeDirectory) {
    }

    public record NetworkInfo(boolean upnpRunning, boolean dlnaEnabled, boolean sonosEnabled, int upnpPort) {
    }

    /**
     * Warnings and errors at the end of the log.
     *
     * @param warnings the warnings among the lines searched
     * @param errors   the errors among the lines searched
     * @param entries  the latest warnings and errors, newest first
     */
    public record LogInfo(String file, boolean readable, int warnings, int errors, List<LogEntry> entries) {
    }

    public record LogEntry(Long time, String level, String logger, String message) {
    }
}
