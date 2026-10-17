package org.airsonic.player.spring;

import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.cache.PlaylistCache.PlaylistUserList;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.Caching;

import java.time.Duration;
import java.util.OptionalLong;

@Configuration
@EnableCaching(proxyTargetClass = true, mode = AdviceMode.ASPECTJ) // AspectJ used so classes calling methods on self can benefit from the cache
public class CacheConfiguration {

    public static final String USER_CACHE = "userCache";
    public static final String USER_SETTINGS_CACHE = "userSettingsCache";
    public static final String MEDIA_FILE_PATH_CACHE = "mediaFilePathCache";
    public static final String MEDIA_FILE_ID_CACHE = "mediaFileIdCache";
    public static final String COVER_ART_CACHE = "coverArtCache";
    public static final String PLAYLIST_CACHE = "playlistCache";
    public static final String PLAYLIST_USERS_CACHE = "playlistUsersCache";

    private static final long MAX_ENTRIES = 1000L;

    @Bean
    public CacheManager jCacheCacheManager() {
        // The provider hands out one manager per URI and class loader, so contexts that are open
        // at the same time (tests) share it and only the first one creates the caches.
        CacheManager cacheManager = Caching.getCachingProvider(CaffeineCachingProvider.class.getName()).getCacheManager();
        createCache(cacheManager, USER_CACHE, String.class, User.class, Duration.ofDays(2));
        createCache(cacheManager, USER_SETTINGS_CACHE, String.class, UserSettings.class, Duration.ofDays(2));
        createCache(cacheManager, MEDIA_FILE_PATH_CACHE, String.class, MediaFile.class, Duration.ofHours(2));
        createCache(cacheManager, MEDIA_FILE_ID_CACHE, Integer.class, MediaFile.class, Duration.ofHours(2));
        createCache(cacheManager, COVER_ART_CACHE, String.class, CoverArt.class, Duration.ofDays(2));
        createCache(cacheManager, PLAYLIST_CACHE, Integer.class, Playlist.class, Duration.ofDays(10));
        createCache(cacheManager, PLAYLIST_USERS_CACHE, Integer.class, PlaylistUserList.class, Duration.ofDays(10));
        return cacheManager;
    }

    private static <K, V> void createCache(CacheManager cacheManager, String name, Class<K> keyType, Class<V> valueType,
            Duration timeToLive) {
        if (cacheManager.getCache(name, keyType, valueType) != null) {
            return;
        }
        cacheManager.createCache(name, new CaffeineConfiguration<K, V>()
                .setTypes(keyType, valueType)
                // entries are shared domain objects, not copies (JCache defaults to copying by serialization)
                .setStoreByValue(false)
                .setMaximumSize(OptionalLong.of(MAX_ENTRIES))
                .setExpireAfterWrite(OptionalLong.of(timeToLive.toNanos())));
    }
}
