package org.airsonic.player.service;

import org.airsonic.player.domain.User;

import java.util.List;

public interface UserService {

    /**
     * Get all users.
     *
     * @return all users.
     */
    public List<User> getAllUsers();

    /**
     * Get system administrator user, which system operations perform on behalf.
     *
     * @return system admin, or {@code null} if there is no admin user.
     */
    User getSysAdmin();

}
