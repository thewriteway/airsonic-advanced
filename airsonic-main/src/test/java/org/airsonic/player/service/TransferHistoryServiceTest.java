package org.airsonic.player.service;

import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserTransferSnapshot;
import org.airsonic.player.repository.UserTransferSnapshotRepository;
import org.airsonic.player.service.TransferHistoryService.DailyTransfers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferHistoryServiceTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 10, 10);

    private final List<UserTransferSnapshot> rows = new ArrayList<>();
    private final User alice = new User("alice", null);
    private final User bob = new User("bob", null);
    private UserTransferSnapshotRepository repository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        repository = mock(UserTransferSnapshotRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> {
            UserTransferSnapshot snapshot = invocation.getArgument(0);
            if (!rows.contains(snapshot)) {
                rows.add(snapshot);
            }
            return snapshot;
        });
        when(repository.existsByUsername(anyString())).thenAnswer(invocation ->
                rows.stream().anyMatch(r -> r.getUsername().equals(invocation.getArgument(0))));
        when(repository.findByUsernameAndSnapshotDate(anyString(), any())).thenAnswer(invocation -> rows.stream()
                .filter(r -> r.getUsername().equals(invocation.getArgument(0)) && r.getSnapshotDate().equals(invocation.getArgument(1)))
                .findFirst());
        when(repository.findBySnapshotDateGreaterThanEqualOrderBySnapshotDate(any())).thenAnswer(invocation ->
                select(null, invocation.getArgument(0)));
        when(repository.findByUsernameAndSnapshotDateGreaterThanEqualOrderBySnapshotDate(anyString(), any())).thenAnswer(invocation ->
                select(invocation.getArgument(0), invocation.getArgument(1)));
        when(repository.findFirstByOrderBySnapshotDate()).thenAnswer(invocation -> first(null));
        when(repository.findFirstByUsernameOrderBySnapshotDate(anyString())).thenAnswer(invocation -> first(invocation.getArgument(0)));
        doAnswer(invocation -> {
            LocalDate before = invocation.getArgument(0);
            rows.removeIf(r -> r.getSnapshotDate().isBefore(before));
            return null;
        }).when(repository).deleteBySnapshotDateBefore(any());

        userService = mock(UserService.class);
        when(userService.getAllUsers()).thenReturn(List.of(alice, bob));
    }

    private List<UserTransferSnapshot> select(String username, LocalDate from) {
        return rows.stream()
                .filter(r -> username == null || r.getUsername().equals(username))
                .filter(r -> !r.getSnapshotDate().isBefore(from))
                .sorted(Comparator.comparing(UserTransferSnapshot::getSnapshotDate))
                .toList();
    }

    private Optional<UserTransferSnapshot> first(String username) {
        return rows.stream().filter(r -> username == null || Objects.equals(r.getUsername(), username))
                .min(Comparator.comparing(UserTransferSnapshot::getSnapshotDate));
    }

    private TransferHistoryService serviceOn(LocalDate date) {
        Clock clock = Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        return new TransferHistoryService(repository, userService, null, clock);
    }

    @Test
    void historyStartsTheDayItIsFirstRecorded() {
        alice.setBytesStreamed(5000);
        TransferHistoryService service = serviceOn(DAY_1);
        assertNull(service.getHistoryStart(null));

        service.snapshot();

        assertEquals(DAY_1, service.getHistoryStart(null));
        // everything before the first snapshot is the baseline, not today's transfers
        List<DailyTransfers> days = service.getDailyTransfers(3, null);
        assertEquals(List.of(
                new DailyTransfers(DAY_1.minusDays(2), 0, 0, 0),
                new DailyTransfers(DAY_1.minusDays(1), 0, 0, 0),
                new DailyTransfers(DAY_1, 0, 0, 0)), days);
    }

    @Test
    void dailyTransfersAreTheGrowthOfTheTotals() {
        alice.setBytesStreamed(1000);
        serviceOn(DAY_1).snapshot();

        alice.setBytesStreamed(1600);
        bob.setBytesDownloaded(300);
        serviceOn(DAY_1).snapshot();

        alice.setBytesStreamed(2000);
        alice.setBytesUploaded(50);
        serviceOn(DAY_1.plusDays(1)).snapshot();

        // after the last snapshot of day 3, the live counters still count for today
        alice.setBytesStreamed(2500);
        List<DailyTransfers> days = serviceOn(DAY_1.plusDays(2)).getDailyTransfers(3, null);

        assertEquals(List.of(
                new DailyTransfers(DAY_1, 600, 300, 0),
                new DailyTransfers(DAY_1.plusDays(1), 400, 0, 50),
                new DailyTransfers(DAY_1.plusDays(2), 500, 0, 0)), days);

        List<DailyTransfers> bobDays = serviceOn(DAY_1.plusDays(2)).getDailyTransfers(3, "bob");
        assertEquals(new DailyTransfers(DAY_1, 0, 300, 0), bobDays.get(0));
        assertEquals(0, bobDays.get(2).streamed());
    }

    @Test
    void oldSnapshotsAreDropped() {
        rows.add(new UserTransferSnapshot("alice", DAY_1.minusDays(TransferHistoryService.MAX_DAYS + 5L), 0, 0, 0));

        serviceOn(DAY_1).snapshot();

        assertEquals(DAY_1.minusDays(1), rows.stream().map(UserTransferSnapshot::getSnapshotDate)
                .min(Comparator.naturalOrder()).orElseThrow());
    }
}
