package org.airsonic.player.service;

import org.airsonic.player.domain.User;

import java.util.List;

public interface UserService {

    /**
     * Get all users.
     *
     * @return all users.
     */
    List<User> getAllUsers();

    /**
     * Get system administrator user, which system operations perform on behalf.
     *
     * @return system admin.
     */
    User getSysAdmin();

}
