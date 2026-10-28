package org.airsonic.player.repository;

import org.airsonic.player.domain.UserTransferSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserTransferSnapshotRepository extends JpaRepository<UserTransferSnapshot, Integer> {

    Optional<UserTransferSnapshot> findByUsernameAndSnapshotDate(String username, LocalDate snapshotDate);

    boolean existsByUsername(String username);

    Optional<UserTransferSnapshot> findFirstByOrderBySnapshotDate();

    Optional<UserTransferSnapshot> findFirstByUsernameOrderBySnapshotDate(String username);

    List<UserTransferSnapshot> findBySnapshotDateGreaterThanEqualOrderBySnapshotDate(LocalDate from);

    List<UserTransferSnapshot> findByUsernameAndSnapshotDateGreaterThanEqualOrderBySnapshotDate(String username,
            LocalDate from);

    @Transactional
    void deleteBySnapshotDateBefore(LocalDate before);
}
