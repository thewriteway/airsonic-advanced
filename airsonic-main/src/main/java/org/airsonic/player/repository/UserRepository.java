package org.airsonic.player.repository;

import org.airsonic.player.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    /**
     * Returns the system administrator, which system operations are performed on behalf of: the "admin"
     * user if it still has the admin role, otherwise any admin.
     */
    default Optional<User> findSysAdmin() {
        return findByUsername(User.USERNAME_ADMIN)
                .filter(User::isAdminRole)
                .or(() -> findAll().stream().filter(User::isAdminRole).findFirst());
    }

}
