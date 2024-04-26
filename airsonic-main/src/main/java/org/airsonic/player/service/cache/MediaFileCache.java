package org.airsonic.player.service.cache;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.spring.CacheConfiguration;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import javax.cache.CacheManager;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;


@Component
public class MediaFileCache {

    private final CacheManager cacheManager;

    public MediaFileCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.cacheManager.enableStatistics(CacheConfiguration.MEDIA_FILE_PATH_CACHE, true);
        this.cacheManager.enableStatistics(CacheConfiguration.MEDIA_FILE_ID_CACHE, true);
    }

    private final AtomicBoolean enabled = new AtomicBoolean(true);

    public void clear() {
        cacheManager.getCache(CacheConfiguration.MEDIA_FILE_PATH_CACHE).clear();
        cacheManager.getCache(CacheConfiguration.MEDIA_FILE_ID_CACHE).clear();
    }

    public void clearPathCache() {
        cacheManager.getCache(CacheConfiguration.MEDIA_FILE_PATH_CACHE).clear();
    }

    public void clearIdCache() {
        cacheManager.getCache(CacheConfiguration.MEDIA_FILE_ID_CACHE).clear();
    }

    public MediaFile getMediaFileByPath(Path path, MusicFolder musicFolder, Double startPosition) {
        if (isDisabled() || path == null || musicFolder == null) {
            return null;
        }
        String key = generatePathKey(path, musicFolder, startPosition);
        return cacheManager.getCache(CacheConfiguration.MEDIA_FILE_PATH_CACHE, String.class, MediaFile.class).get(key);
    }

    public void putMediaFileByPath(Path path, MusicFolder musicFolder, Double startPosition, MediaFile mediaFile) {
        if (isDisabled() || mediaFile == null || path == null || musicFolder == null) {
            return;
        }
        String key = generatePathKey(path, musicFolder, startPosition);
        cacheManager.getCache(CacheConfiguration.MEDIA_FILE_PATH_CACHE, String.class, MediaFile.class).put(key, mediaFile);
    }

    public MediaFile getMediaFileById(Integer id) {
        if (isDisabled() || id == null) {
            return null;
        }
        return cacheManager.getCache(CacheConfiguration.MEDIA_FILE_ID_CACHE, Integer.class, MediaFile.class).get(id);
    }

    public void putMediaFileById(Integer id, MediaFile mediaFile) {
        if (isDisabled() || mediaFile == null || id == null) {
            return;
        }
        cacheManager.getCache(CacheConfiguration.MEDIA_FILE_ID_CACHE, Integer.class, MediaFile.class).put(id,
                mediaFile);
    }

    public void removeMediaFile(MediaFile mediaFile) {
        if (isDisabled() || mediaFile == null) {
            return;
        }
        cacheManager.getCache(CacheConfiguration.MEDIA_FILE_PATH_CACHE, String.class, MediaFile.class)
                .remove(generatePathKey(mediaFile.getRelativePath(), mediaFile.getFolder(), mediaFile.getStartPosition()));
        if (mediaFile.getId() != null) {
            cacheManager.getCache(CacheConfiguration.MEDIA_FILE_ID_CACHE, Integer.class, MediaFile.class)
                .remove(mediaFile.getId());
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }


    private boolean isDisabled() {
        return !enabled.get();
    }

    private String generatePathKey(@Nonnull Path path, @Nonnull MusicFolder musicFolder, Double startPosition) {
        return path.toString().concat("-").concat(musicFolder.getId().toString()).concat("-").concat(startPosition == null ? "" : startPosition.toString());
    }

}
