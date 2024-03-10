package org.airsonic.player.repository;

import org.airsonic.player.domain.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, Integer> {

    List<Playlist> findByUsername(String username);

    List<Playlist> findByUsernameOrderByNameAsc(String username);

    List<Playlist> findBySharedTrue();

    List<Playlist> findByUsernameNotAndSharedUsersUsername(String username, String sharedUsername);

    Optional<Playlist> findByIdAndSharedUsersUsername(Integer id, String sharedUsername);

    boolean existsByIdAndUsername(Integer id, String username);

}