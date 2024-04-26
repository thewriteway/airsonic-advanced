package org.airsonic.player.service.cache;

import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.spring.CacheConfiguration;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import javax.cache.CacheManager;

@Component
public class UserSettingsCache {

    private final CacheManager cacheManager;

    public UserSettingsCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.cacheManager.enableStatistics(CacheConfiguration.USER_SETTINGS_CACHE, true);
    }

    public void clear() {
        cacheManager.getCache(CacheConfiguration.USER_SETTINGS_CACHE).clear();
    }

    /**
     * Get the user settings for the given username.
     *
     * @param username The username to get the settings for. If null, returns null.
     * @return The user settings for the given username, or null if the username is null.
     */
    public UserSettings getUserSettings(@Nullable String username) {
        if (username == null) {
            return null;
        }
        return cacheManager.getCache(CacheConfiguration.USER_SETTINGS_CACHE, String.class, UserSettings.class)
                .get(username);
    }

    public void putUserSettings(String username, UserSettings userSettings) {
        if (username == null || userSettings == null) {
            return;
        }
        cacheManager.getCache(CacheConfiguration.USER_SETTINGS_CACHE, String.class, UserSettings.class)
                .put(username, userSettings);
    }

    public void removeUserSettings(String username) {
        if (username == null) {
            return;
        }
        cacheManager.getCache(CacheConfiguration.USER_SETTINGS_CACHE, String.class, UserSettings.class)
                .remove(username);
    }

}
