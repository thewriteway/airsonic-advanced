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
import org.airsonic.player.service.PathWatcherService.PathWatcherEndpoint;
import org.airsonic.player.service.StatusReport.CacheStats;
import org.airsonic.player.service.StatusReport.Sessions;
import org.airsonic.player.service.StatusReport.TaskInfo;
import org.airsonic.player.service.StatusReport.WatchedPath;
import org.airsonic.player.service.TaskSchedulingService.CustomScheduledTasksEndpoint;
import org.airsonic.player.service.TaskSchedulingService.CustomScheduledTasksEndpoint.RunMetadata;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.TaskExecutionOutcome;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The parts of the status report that come from the metrics and endpoints of the server: sessions, caches,
 * scheduled tasks and path watchers.
 */
@Service
public class ServerMetricsService {

    private static final String CACHE_GETS = "cache.gets";
    private static final String CACHE_TAG = "cache";

    private final MeterRegistry meterRegistry;
    private final CustomScheduledTasksEndpoint scheduledTasksEndpoint;
    private final PathWatcherEndpoint pathWatcherEndpoint;

    public ServerMetricsService(MeterRegistry meterRegistry, CustomScheduledTasksEndpoint scheduledTasksEndpoint,
            PathWatcherEndpoint pathWatcherEndpoint) {
        this.meterRegistry = meterRegistry;
        this.scheduledTasksEndpoint = scheduledTasksEndpoint;
        this.pathWatcherEndpoint = pathWatcherEndpoint;
    }

    public Sessions getSessions() {
        return new Sessions(
                toLong(meterValue("tomcat.sessions.active.current")),
                toLong(meterValue("tomcat.sessions.created")),
                toLong(meterValue("tomcat.sessions.expired")),
                toLong(meterValue("tomcat.sessions.rejected")));
    }

    public List<CacheStats> getCaches() {
        return meterRegistry.find(CACHE_GETS).meters().stream()
                .map(meter -> meter.getId().getTag(CACHE_TAG))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(name -> {
                    Long hits = toLong(meterValue(CACHE_GETS, CACHE_TAG, name, "result", "hit"));
                    Long misses = toLong(meterValue(CACHE_GETS, CACHE_TAG, name, "result", "miss"));
                    Double hitRatio = hits != null && misses != null && hits + misses > 0
                            ? (double) hits / (hits + misses)
                            : null;
                    return new CacheStats(name, hitRatio, hits, misses,
                            toLong(meterValue("cache.puts", CACHE_TAG, name)),
                            toLong(meterValue("cache.removals", CACHE_TAG, name)),
                            toLong(meterValue("cache.size", CACHE_TAG, name)));
                })
                .toList();
    }

    /** The sum of the first measurement of the matching meters, or null if there is none. */
    private Double meterValue(String name, String... tags) {
        Double result = null;
        for (Meter meter : meterRegistry.find(name).tags(tags).meters()) {
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

    public List<TaskInfo> getTasks() {
        Map<String, Map<String, Object>> tasks = scheduledTasksEndpoint.info().getBody();
        if (tasks == null) {
            return List.of();
        }
        return tasks.entrySet().stream()
                .map(e -> toTaskInfo(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(TaskInfo::name))
                .toList();
    }

    private static TaskInfo toTaskInfo(String key, Map<String, Object> task) {
        Optional<RunMetadata> run = Optional.ofNullable((RunMetadata) task.get("runMetadata"));
        Optional<ScheduledTask> scheduledTask = Optional.ofNullable((ScheduledTask) task.get("scheduledTask"));
        // Spring tracks the actual executions; the run metadata can only estimate them
        Optional<TaskExecutionOutcome> outcome = scheduledTask
                .map(t -> t.getTask().getLastExecutionOutcome())
                .filter(o -> o.executionTime() != null);
        Long lastRun = outcome.map(TaskExecutionOutcome::executionTime).map(Instant::toEpochMilli)
                .orElseGet(() -> run.map(RunMetadata::getLastRun).map(ServerMetricsService::toMillis).orElse(null));
        Long nextRun = scheduledTask.map(ScheduledTask::nextExecution).map(Instant::toEpochMilli)
                .orElseGet(() -> run.map(RunMetadata::getNextRun).map(ServerMetricsService::toMillis).orElse(null));
        return new TaskInfo(
                Optional.ofNullable((String) task.get("name")).orElse(key),
                run.map(RunMetadata::getType).map(Enum::name).orElse(null),
                toMillis((Instant) task.get("created")),
                run.map(RunMetadata::getFirstRun).map(ServerMetricsService::toMillis).orElse(null),
                lastRun,
                outcome.map(o -> o.status().name()).orElse(null),
                outcome.map(TaskExecutionOutcome::throwable).map(Throwable::toString).orElse(null),
                nextRun,
                // the runnable is wrapped by Spring, whose wrapper describes itself as the task it runs
                simpleClassName(scheduledTask.map(t -> t.getTask().toString())
                        .orElse((String) task.get("scheduledBy"))));
    }

    private static Long toMillis(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Instant.MIN.equals(instant) ? StatusReport.UNKNOWN_TIME : instant.toEpochMilli();
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

    public List<WatchedPath> getPathWatchers() {
        Map<String, String> watchers = pathWatcherEndpoint.info().getBody();
        if (watchers == null) {
            return List.of();
        }
        return watchers.entrySet().stream()
                .map(e -> new WatchedPath(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(WatchedPath::name))
                .toList();
    }
}
