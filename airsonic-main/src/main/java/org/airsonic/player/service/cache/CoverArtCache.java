package org.airsonic.player.service.cache;

import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.spring.CacheConfiguration;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import javax.cache.CacheManager;

@Component
public class CoverArtCache {

    private final CacheManager cacheManager;

    public CoverArtCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.cacheManager.enableStatistics(CacheConfiguration.COVER_ART_CACHE, true);
    }

    public void clear() {
        cacheManager.getCache(CacheConfiguration.COVER_ART_CACHE).clear();
    }

    public CoverArt getCoverArt(CoverArt.EntityType entityType, Integer entityId) {
        if (entityType == null || entityId == null) {
            return null;
        }
        return cacheManager.getCache(CacheConfiguration.COVER_ART_CACHE, String.class, CoverArt.class)
                .get(generateKey(entityType, entityId));
    }

    public void putCoverArt(CoverArt art) {
        if (art == null || art.equals(CoverArt.NULL_ART) || art.getEntityType() == null || art.getEntityId() == null) {
            return;
        }
        cacheManager.getCache(CacheConfiguration.COVER_ART_CACHE, String.class, CoverArt.class)
                .put(generateKey(art.getEntityType(), art.getEntityId()), art);
    }

    public void removeCoverArt(CoverArt art) {
        if (art == null || art.equals(CoverArt.NULL_ART) || art.getEntityType() == null || art.getEntityId() == null) {
            return;
        }
        cacheManager.getCache(CacheConfiguration.COVER_ART_CACHE, String.class, CoverArt.class)
                .remove(generateKey(art.getEntityType(), art.getEntityId()));
    }

    public void removeCoverArt(CoverArt.EntityType entityType, Integer entityId) {
        if (entityType == null || entityId == null) {
            return;
        }
        cacheManager.getCache(CacheConfiguration.COVER_ART_CACHE, String.class, CoverArt.class)
                .remove(generateKey(entityType, entityId));
    }

    private String generateKey(@Nonnull CoverArt.EntityType entityType, @Nonnull Integer entityId) {
        return entityType.toString().concat("-").concat(entityId.toString());
    }

}
