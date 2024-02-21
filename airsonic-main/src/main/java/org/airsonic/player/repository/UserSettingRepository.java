package org.airsonic.player.repository;

import org.airsonic.player.domain.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSettingRepository extends JpaRepository<UserSetting, String> {
    Optional<UserSetting> findByUsername(String username);
}
