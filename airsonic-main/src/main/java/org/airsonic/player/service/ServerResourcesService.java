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

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.service.StatusReport.NetworkInfo;
import org.airsonic.player.service.StatusReport.PlaybackInfo;
import org.airsonic.player.service.StatusReport.StorageInfo;
import org.airsonic.player.service.StatusReport.StorageKind;
import org.airsonic.player.service.StatusReport.SystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import jakarta.servlet.ServletContext;
import javax.sql.DataSource;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The parts of the status report about the machine and the services Airsonic runs: system, storage, playback
 * and network.
 */
@Service
public class ServerResourcesService {

    private static final Logger LOG = LoggerFactory.getLogger(ServerResourcesService.class);

    /** How long the transcoder lookup is reused; a failed lookup starts processes and is not cached elsewhere. */
    private static final long TRANSCODER_CHECK_MILLIS = 10L * 60L * 1000L;

    private final VersionService versionService;
    private final SettingsService settingsService;
    private final MediaFolderService mediaFolderService;
    private final UPnPService upnpService;
    private final StatusService statusService;
    private final AirsonicHomeConfig homeConfig;
    private final DataSource dataSource;
    /** Absent when the application runs without a web server, e.g. in some tests. */
    private final ObjectProvider<ServletContext> servletContext;
    private volatile String database;
    private volatile TranscoderLookup transcoderLookup;

    public ServerResourcesService(VersionService versionService, SettingsService settingsService,
            MediaFolderService mediaFolderService, UPnPService upnpService, StatusService statusService,
            AirsonicHomeConfig homeConfig, DataSource dataSource, ObjectProvider<ServletContext> servletContext) {
        this.versionService = versionService;
        this.settingsService = settingsService;
        this.mediaFolderService = mediaFolderService;
        this.upnpService = upnpService;
        this.statusService = statusService;
        this.homeConfig = homeConfig;
        this.dataSource = dataSource;
        this.servletContext = servletContext;
    }

    public SystemInfo getSystem() {
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

    public List<StorageInfo> getStorage() {
        List<StorageInfo> result = new ArrayList<>();
        result.add(storage(null, homeConfig.getAirsonicHome(), StorageKind.HOME, true));
        mediaFolderService.getAllMusicFolders(true, true).stream()
                .sorted(Comparator.comparing(MusicFolder::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(folder -> storage(folder.getName(), folder.getPath(), StorageKind.MUSIC, folder.isEnabled()))
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

    /**
     * @param activeStreams the number of streams running
     */
    public PlaybackInfo getPlayback(int activeStreams) {
        long players = statusService.getAllStreamStatuses().stream()
                .filter(TransferStatus::isActive)
                .map(TransferStatus::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getId)
                .distinct()
                .count();
        return new PlaybackInfo((int) players, activeStreams, getTranscoder(),
                homeConfig.getTranscodeDirectory().toString());
    }

    private String getTranscoder() {
        long now = System.currentTimeMillis();
        TranscoderLookup lookup = transcoderLookup;
        if (lookup == null || now - lookup.checked() > TRANSCODER_CHECK_MILLIS) {
            lookup = new TranscoderLookup(settingsService.resolveTranscodeExecutable("ffmpeg", null), now);
            transcoderLookup = lookup;
        }
        return lookup.executable();
    }

    public NetworkInfo getNetwork() {
        return new NetworkInfo(upnpService.isRunning(), settingsService.isDlnaEnabled(), settingsService.isSonosEnabled(),
                settingsService.getUPnpPort());
    }

    /**
     * @param executable the ffmpeg found, or null
     * @param checked    when it was looked up, in milliseconds since the epoch
     */
    private record TranscoderLookup(String executable, long checked) {
    }
}
