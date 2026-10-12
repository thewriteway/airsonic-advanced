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

import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserTransferSnapshot;
import org.airsonic.player.repository.UserTransferSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Keeps a daily history of the bytes each user streamed, downloaded and uploaded.
 *
 * The users only have running totals, so the totals are saved once per day and per user (and updated every hour
 * during the day); the transfers of a day are the difference to the previous saved day. The first time a user is
 * seen, a baseline for the day before is saved as well, so that the history starts with that day instead of
 * attributing every earlier transfer to it.
 */
@Service
public class TransferHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(TransferHistoryService.class);

    public static final int MAX_DAYS = 366;
    private static final Duration SNAPSHOT_INTERVAL = Duration.ofHours(1);

    private final UserTransferSnapshotRepository repository;
    private final UserService userService;
    private final TaskSchedulingService taskService;
    private final Clock clock;

    @Autowired
    public TransferHistoryService(UserTransferSnapshotRepository repository, UserService userService,
            TaskSchedulingService taskService) {
        this(repository, userService, taskService, Clock.systemDefaultZone());
    }

    TransferHistoryService(UserTransferSnapshotRepository repository, UserService userService,
            TaskSchedulingService taskService, Clock clock) {
        this.repository = repository;
        this.userService = userService;
        this.taskService = taskService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void schedule() {
        taskService.scheduleAtFixedRate("transfer-history-snapshot", this::snapshotSafely,
                Instant.now(clock).plusSeconds(60), SNAPSHOT_INTERVAL, true);
    }

    private void snapshotSafely() {
        try {
            snapshot();
        } catch (Exception e) {
            LOG.warn("Could not save the transfer history", e);
        }
    }

    /**
     * Saves today's totals of every user and drops the snapshots that are too old to be shown.
     */
    @Transactional
    public void snapshot() {
        LocalDate today = LocalDate.now(clock);
        for (User user : userService.getAllUsers()) {
            if (!repository.existsByUsername(user.getUsername())) {
                repository.save(new UserTransferSnapshot(user.getUsername(), today.minusDays(1),
                        user.getBytesStreamed(), user.getBytesDownloaded(), user.getBytesUploaded()));
            }
            UserTransferSnapshot snapshot = repository.findByUsernameAndSnapshotDate(user.getUsername(), today)
                    .orElseGet(() -> new UserTransferSnapshot(user.getUsername(), today, 0, 0, 0));
            snapshot.setTotals(user.getBytesStreamed(), user.getBytesDownloaded(), user.getBytesUploaded());
            repository.save(snapshot);
        }
        repository.deleteBySnapshotDateBefore(today.minusDays(MAX_DAYS + 1L));
    }

    /**
     * Returns the bytes transferred on each of the last days, oldest first, including today up to now.
     *
     * @param days     the number of days, today included (1 to {@link #MAX_DAYS})
     * @param username the user, or null for all users together
     */
    public List<DailyTransfers> getDailyTransfers(int days, String username) {
        int count = Math.max(1, Math.min(MAX_DAYS, days));
        LocalDate today = LocalDate.now(clock);
        LocalDate first = today.minusDays(count - 1L);
        // one more day for the baseline of the first day
        List<UserTransferSnapshot> snapshots = username == null
                ? repository.findBySnapshotDateGreaterThanEqualOrderBySnapshotDate(first.minusDays(1))
                : repository.findByUsernameAndSnapshotDateGreaterThanEqualOrderBySnapshotDate(username, first.minusDays(1));

        // totals per user and day; today comes from the live counters, which are newer than its snapshot
        Map<String, TreeMap<LocalDate, long[]>> totals = new HashMap<>();
        for (UserTransferSnapshot snapshot : snapshots) {
            totals.computeIfAbsent(snapshot.getUsername(), u -> new TreeMap<>()).put(snapshot.getSnapshotDate(),
                    new long[] {snapshot.getBytesStreamed(), snapshot.getBytesDownloaded(), snapshot.getBytesUploaded()});
        }
        for (User user : userService.getAllUsers()) {
            TreeMap<LocalDate, long[]> userTotals = totals.get(user.getUsername());
            if (userTotals != null && (username == null || Objects.equals(username, user.getUsername()))) {
                userTotals.put(today, new long[] {user.getBytesStreamed(), user.getBytesDownloaded(), user.getBytesUploaded()});
            }
        }

        Map<LocalDate, long[]> perDay = new TreeMap<>();
        for (TreeMap<LocalDate, long[]> userTotals : totals.values()) {
            long[] previous = null;
            for (Map.Entry<LocalDate, long[]> entry : userTotals.entrySet()) {
                long[] current = entry.getValue();
                if (previous != null && !entry.getKey().isBefore(first)) {
                    long[] day = perDay.computeIfAbsent(entry.getKey(), d -> new long[3]);
                    for (int i = 0; i < 3; i++) {
                        // counters only grow, unless a user was deleted and created again
                        day[i] += Math.max(0, current[i] - previous[i]);
                    }
                }
                previous = current;
            }
        }

        List<DailyTransfers> result = new ArrayList<>(count);
        for (LocalDate date = first; !date.isAfter(today); date = date.plusDays(1)) {
            long[] day = perDay.getOrDefault(date, new long[3]);
            result.add(new DailyTransfers(date, day[0], day[1], day[2]));
        }
        return result;
    }

    /**
     * Returns the day the history starts, i.e. the first day whose transfers are known.
     *
     * @param username the user, or null for all users
     * @return the day, or null if nothing has been recorded yet
     */
    public LocalDate getHistoryStart(String username) {
        return (username == null ? repository.findFirstByOrderBySnapshotDate()
                : repository.findFirstByUsernameOrderBySnapshotDate(username))
                .map(snapshot -> snapshot.getSnapshotDate().plusDays(1))
                .orElse(null);
    }

    public record DailyTransfers(LocalDate date, long streamed, long downloaded, long uploaded) {
    }
}
