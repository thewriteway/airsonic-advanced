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

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaLibraryStatistics;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.TransferStatus.Sample;
import org.airsonic.player.service.PathWatcherService.PathWatcherEndpoint;
import org.airsonic.player.service.TaskSchedulingService.CustomScheduledTasksEndpoint;
import org.airsonic.player.service.TaskSchedulingService.CustomScheduledTasksEndpoint.RunMetadata;
import org.airsonic.player.service.search.IndexManager;
import org.airsonic.player.util.FileUtil;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.TaskExecutionOutcome;
import org.springframework.stereotype.Service;

import jakarta.servlet.ServletContext;
import javax.sql.DataSource;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Collects everything the status page shows in a single report.
 *
 * Administrators get the whole report. Other users only get their own transfers and the library
 * summary; the sections about the server are left out (null).
 */
@Service
public class StatusReportService {

    private static final Logger LOG = LoggerFactory.getLogger(StatusReportService.class);

    /** Recently finished streams are listed for this long. */
    private static final long RECENT_STREAM_MILLIS = 60L * 60L * 1000L;
    /** A transfer with no new sample for this long is idle. */
    private static final long IDLE_MILLIS = 15_000L;
    /** Number of bitrate points sent per transfer (one sample every five seconds). */
    private static final int BITRATE_POINTS = 60;
    /** Marks a run time that cannot be calculated, e.g. the next run of a fixed delay task. */
    public static final long UNKNOWN_TIME = -1L;
    /** Number of log lines searched for warnings and errors, from the end of the log. */
    private static final int LOG_LINES_SCANNED = 2000;
    /** Number of warnings and errors reported. */
    private static final int LOG_ENTRIES = 15;
    /** A line of logging.pattern.file: "2026-10-12 10:57:27.226  WARN --- logger : message". */
    private static final Pattern LOG_LINE = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(WARN|ERROR)\\s+---\\s+(.*?)\\s+:\\s(.*)$");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    /** How long the transcoder lookup is reused; a failed lookup starts processes and is not cached elsewhere. */
    private static final long TRANSCODER_CHECK_MILLIS = 10L * 60L * 1000L;

    private final StatusService statusService;
    private final UserService userService;
    private final IndexManager indexManager;
    private final MediaScannerService mediaScannerService;
    private final MeterRegistry meterRegistry;
    private final CustomScheduledTasksEndpoint scheduledTasksEndpoint;
    private final PathWatcherEndpoint pathWatcherEndpoint;
    private final VersionService versionService;
    private final SettingsService settingsService;
    private final MediaFolderService mediaFolderService;
    private final UPnPService upnpService;
    private final AirsonicHomeConfig homeConfig;
    private final DataSource dataSource;
    /** Absent when the application runs without a web server, e.g. in some tests. */
    private final ObjectProvider<ServletContext> servletContext;
    private volatile String database;
    private volatile Optional<String> transcoder;
    private volatile long transcoderChecked;

    public StatusReportService(StatusService statusService, UserService userService, IndexManager indexManager,
            MediaScannerService mediaScannerService, MeterRegistry meterRegistry,
            CustomScheduledTasksEndpoint scheduledTasksEndpoint, PathWatcherEndpoint pathWatcherEndpoint,
            VersionService versionService, SettingsService settingsService, MediaFolderService mediaFolderService,
            UPnPService upnpService, AirsonicHomeConfig homeConfig, DataSource dataSource,
            ObjectProvider<ServletContext> servletContext) {
        this.statusService = statusService;
        this.userService = userService;
        this.indexManager = indexManager;
        this.mediaScannerService = mediaScannerService;
        this.meterRegistry = meterRegistry;
        this.scheduledTasksEndpoint = scheduledTasksEndpoint;
        this.pathWatcherEndpoint = pathWatcherEndpoint;
        this.versionService = versionService;
        this.settingsService = settingsService;
        this.mediaFolderService = mediaFolderService;
        this.upnpService = upnpService;
        this.homeConfig = homeConfig;
        this.dataSource = dataSource;
        this.servletContext = servletContext;
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

        return new StatusReport(
                System.currentTimeMillis(),
                admin,
                getLibrary(),
                transfers,
                users,
                admin ? ManagementFactory.getRuntimeMXBean().getUptime() : null,
                admin ? getSessions() : null,
                admin ? getCaches() : null,
                admin ? getTasks() : null,
                admin ? getPathWatchers() : null,
                admin ? getSystem() : null,
                admin ? getStorage() : null,
                admin ? getPlayback(transfers) : null,
                admin ? getNetwork() : null,
                admin ? getLog() : null);
    }

    private List<Transfer> getTransfers(Predicate<TransferStatus> visible) {
        Stream<Transfer> streams = statusService.getAllStreamStatuses().stream()
                .filter(status -> status.isActive() || status.getMillisSinceLastUpdate() < RECENT_STREAM_MILLIS)
                .filter(visible)
                .map(status -> toTransfer(status, TransferType.stream));
        Stream<Transfer> downloads = statusService.getAllDownloadStatuses().stream()
                .filter(visible)
                .map(status -> toTransfer(status, TransferType.download));
        Stream<Transfer> uploads = statusService.getAllUploadStatuses().stream()
                .filter(visible)
                .map(status -> toTransfer(status, TransferType.upload));
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

        List<BitratePoint> bitrate = new ArrayList<>();
        Sample previous = null;
        for (Iterator<Sample> it = status.getHistory().iterator(); it.hasNext();) {
            Sample sample = it.next();
            if (previous != null) {
                long elapsed = sample.getTimestamp() - previous.getTimestamp();
                if (elapsed > 0) {
                    long bytes = Math.max(0, sample.getBytesTransferred() - previous.getBytesTransferred());
                    bitrate.add(new BitratePoint(sample.getTimestamp(), toKbps(bytes, elapsed)));
                }
            }
            previous = sample;
        }
        if (bitrate.size() > BITRATE_POINTS) {
            bitrate = new ArrayList<>(bitrate.subList(bitrate.size() - BITRATE_POINTS, bitrate.size()));
        }
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

    private static double toKbps(long bytes, long millis) {
        return (8.0 * bytes / 1024.0) / (millis / 1000.0);
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

    private Sessions getSessions() {
        return new Sessions(
                toLong(meterValue("tomcat.sessions.active.current")),
                toLong(meterValue("tomcat.sessions.created")),
                toLong(meterValue("tomcat.sessions.expired")),
                toLong(meterValue("tomcat.sessions.rejected")));
    }

    private List<CacheStats> getCaches() {
        return meterRegistry.find("cache.gets").meters().stream()
                .map(meter -> meter.getId().getTag("cache"))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(name -> {
                    Long hits = toLong(meterValue("cache.gets", "cache", name, "result", "hit"));
                    Long misses = toLong(meterValue("cache.gets", "cache", name, "result", "miss"));
                    Double hitRatio = hits != null && misses != null && hits + misses > 0
                            ? (double) hits / (hits + misses)
                            : null;
                    return new CacheStats(name, hitRatio, hits, misses,
                            toLong(meterValue("cache.puts", "cache", name)),
                            toLong(meterValue("cache.removals", "cache", name)),
                            toLong(meterValue("cache.size", "cache", name)));
                })
                .toList();
    }

    /** The sum of the first measurement of the matching meters, or null if there is none. */
    private Double meterValue(String name, String... tags) {
        List<Meter> meters = new ArrayList<>(meterRegistry.find(name).tags(tags).meters());
        Double result = null;
        for (Meter meter : meters) {
            Iterator<Measurement> measurements = meter.measure().iterator();
            if (measurements.hasNext()) {
                double value = measurements.next().getValue();
                if (Double.isFinite(value)) {
                    result = (result == null ? 0 : result) + value;
                }
            }
        }
        return result;
    }

    private static Long toLong(Double value) {
        return value == null ? null : Math.round(value);
    }

    @SuppressWarnings("unchecked")
    private List<TaskInfo> getTasks() {
        Map<String, Map<String, Object>> tasks = scheduledTasksEndpoint.info().getBody();
        if (tasks == null) {
            return List.of();
        }
        return tasks.entrySet().stream().map(e -> {
            Map<String, Object> task = e.getValue();
            Optional<RunMetadata> run = Optional.ofNullable((RunMetadata) task.get("runMetadata"));
            Optional<ScheduledTask> scheduledTask = Optional.ofNullable((ScheduledTask) task.get("scheduledTask"));
            // Spring tracks the actual executions; the run metadata can only estimate them
            Optional<TaskExecutionOutcome> outcome = scheduledTask
                    .map(t -> t.getTask().getLastExecutionOutcome())
                    .filter(o -> o.executionTime() != null);
            Long lastRun = outcome.map(TaskExecutionOutcome::executionTime).map(Instant::toEpochMilli)
                    .orElseGet(() -> run.map(RunMetadata::getLastRun).map(StatusReportService::toMillis).orElse(null));
            Long nextRun = scheduledTask.map(ScheduledTask::nextExecution).map(Instant::toEpochMilli)
                    .orElseGet(() -> run.map(RunMetadata::getNextRun).map(StatusReportService::toMillis).orElse(null));
            return new TaskInfo(
                    Optional.ofNullable((String) task.get("name")).orElse(e.getKey()),
                    run.map(RunMetadata::getType).map(Enum::name).orElse(null),
                    toMillis((Instant) task.get("created")),
                    run.map(RunMetadata::getFirstRun).map(StatusReportService::toMillis).orElse(null),
                    lastRun,
                    outcome.map(o -> o.status().name()).orElse(null),
                    outcome.map(TaskExecutionOutcome::throwable).map(Throwable::toString).orElse(null),
                    nextRun,
                    // the runnable is wrapped by Spring, whose wrapper describes itself as the task it runs
                    simpleClassName(scheduledTask.map(t -> t.getTask().toString())
                            .orElse((String) task.get("scheduledBy"))));
        }).sorted(Comparator.comparing(TaskInfo::name)).toList();
    }

    private static Long toMillis(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Instant.MIN.equals(instant) ? UNKNOWN_TIME : instant.toEpochMilli();
    }

    /**
     * Turns the description of a scheduled runnable into a readable name, e.g.
     * {@code org.airsonic.player.service.MediaScannerService$$Lambda/0x1234@5678} into {@code MediaScannerService}.
     */
    static String simpleClassName(String className) {
        if (className == null) {
            return null;
        }
        String name = className;
        int hash = name.indexOf('@');
        if (hash >= 0) {
            name = name.substring(0, hash);
        }
        int lambda = name.indexOf("$$");
        if (lambda >= 0) {
            name = name.substring(0, lambda);
        }
        name = name.substring(name.lastIndexOf('.') + 1);
        int inner = name.indexOf('$');
        return inner > 0 ? name.substring(0, inner) : name;
    }

    private List<WatchedPath> getPathWatchers() {
        Map<String, String> watchers = pathWatcherEndpoint.info().getBody();
        if (watchers == null) {
            return List.of();
        }
        return watchers.entrySet().stream()
                .map(e -> new WatchedPath(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(WatchedPath::name))
                .toList();
    }

    private SystemInfo getSystem() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Double processCpu = null;
        Double systemCpu = null;
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            processCpu = toRatio(sunOs.getProcessCpuLoad());
            systemCpu = toRatio(sunOs.getCpuLoad());
        }
        return new SystemInfo(
                Optional.ofNullable(versionService.getLocalVersion()).map(Object::toString).orElse(null),
                Optional.ofNullable(versionService.getLocalBuildDate()).map(Instant::toEpochMilli).orElse(null),
                versionService.getLocalBuildNumber(),
                System.getProperty("java.vendor") + " " + Runtime.version(),
                System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")",
                Optional.ofNullable(servletContext.getIfAvailable()).map(ServletContext::getServerInfo).orElse(null),
                getDatabase(),
                ManagementFactory.getRuntimeMXBean().getStartTime(),
                heap.getUsed(),
                heap.getMax() > 0 ? heap.getMax() : heap.getCommitted(),
                processCpu,
                systemCpu,
                os.getAvailableProcessors(),
                ManagementFactory.getThreadMXBean().getThreadCount());
    }

    /** A CPU load between 0 and 1, or null if it is not available (negative). */
    private static Double toRatio(double load) {
        return load < 0 || Double.isNaN(load) ? null : load;
    }

    private String getDatabase() {
        if (database == null) {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                database = metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
            } catch (Exception e) {
                LOG.debug("Could not read the database version", e);
                return null;
            }
        }
        return database;
    }

    private List<StorageInfo> getStorage() {
        List<StorageInfo> result = new ArrayList<>();
        result.add(storage(null, homeConfig.getAirsonicHome(), StorageKind.home, true));
        mediaFolderService.getAllMusicFolders(true, true).stream()
                .sorted(Comparator.comparing(MusicFolder::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(folder -> storage(folder.getName(), folder.getPath(), StorageKind.music, folder.isEnabled()))
                .forEach(result::add);
        return result;
    }

    private static StorageInfo storage(String name, Path path, StorageKind kind, boolean enabled) {
        boolean exists = path != null && Files.isDirectory(path);
        Long free = null;
        Long total = null;
        if (exists) {
            try {
                FileStore store = Files.getFileStore(path);
                free = store.getUsableSpace();
                total = store.getTotalSpace();
            } catch (IOException e) {
                LOG.debug("Could not read the disk space of {}", path, e);
            }
        }
        return new StorageInfo(name, path == null ? null : path.toString(), kind, enabled, exists, free, total);
    }

    private PlaybackInfo getPlayback(List<Transfer> transfers) {
        long players = statusService.getAllStreamStatuses().stream()
                .filter(TransferStatus::isActive)
                .map(TransferStatus::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getId)
                .distinct()
                .count();
        long streams = transfers.stream().filter(t -> t.active() && t.type() == TransferType.stream).count();
        return new PlaybackInfo((int) players, (int) streams, getTranscoder(),
                homeConfig.getTranscodeDirectory().toString());
    }

    private String getTranscoder() {
        long now = System.currentTimeMillis();
        if (transcoder == null || now - transcoderChecked > TRANSCODER_CHECK_MILLIS) {
            transcoder = Optional.ofNullable(settingsService.resolveTranscodeExecutable("ffmpeg", null));
            transcoderChecked = now;
        }
        return transcoder.orElse(null);
    }

    private NetworkInfo getNetwork() {
        return new NetworkInfo(upnpService.isRunning(), settingsService.isDlnaEnabled(), settingsService.isSonosEnabled(),
                settingsService.getUPnpPort());
    }

    private LogInfo getLog() {
        String file = settingsService.getLogFile();
        Path path = file == null ? null : Paths.get(file);
        if (path == null || !Files.isRegularFile(path)) {
            return new LogInfo(file, false, 0, 0, List.of());
        }
        List<String> lines = new ArrayList<>();
        try (ReversedLinesFileReader reader = ReversedLinesFileReader.builder().setFile(path.toFile())
                .setCharset(StandardCharsets.UTF_8).get()) {
            String line;
            while (lines.size() < LOG_LINES_SCANNED && (line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            LOG.debug("Could not read the log file {}", path, e);
            return new LogInfo(file, false, 0, 0, List.of());
        }
        // newest first
        List<LogEntry> entries = parseLogEntries(lines);
        int warnings = (int) entries.stream().filter(e -> "WARN".equals(e.level())).count();
        return new LogInfo(file, true, warnings, entries.size() - warnings,
                entries.subList(0, Math.min(LOG_ENTRIES, entries.size())));
    }

    /**
     * Picks the warnings and errors out of log lines (in the order given). Continuation lines, e.g. stack traces,
     * are skipped.
     */
    static List<LogEntry> parseLogEntries(List<String> lines) {
        List<LogEntry> entries = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = LOG_LINE.matcher(line);
            if (matcher.matches()) {
                Long time;
                try {
                    time = LocalDateTime.parse(matcher.group(1), LOG_TIME).atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli();
                } catch (DateTimeParseException e) {
                    time = null;
                }
                entries.add(new LogEntry(time, matcher.group(2), matcher.group(3).trim(), matcher.group(4)));
            }
        }
        return entries;
    }

    public enum TransferType {
        stream, download, upload
    }

    /**
     * Everything shown on the status page. Sections that are only for administrators are null for other users.
     */
    public record StatusReport(long generated, boolean admin, Library library, List<Transfer> transfers,
            List<UserTotals> users, Long uptimeMillis, Sessions sessions, List<CacheStats> caches,
            List<TaskInfo> tasks, List<WatchedPath> pathWatchers, SystemInfo system, List<StorageInfo> storage,
            PlaybackInfo playback, NetworkInfo network, LogInfo log) {
    }

    /**
     * A transfer. Times are milliseconds since the epoch, rates are kbit/s.
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
     * @param lastScan     milliseconds since the epoch, or null if the library was never scanned
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
     * Run times are milliseconds since the epoch, null if the task does not run then and
     * {@link StatusReportService#UNKNOWN_TIME} if the time cannot be calculated.
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
     * @param startTime  when the server started, milliseconds since the epoch
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
        home,
        music
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
