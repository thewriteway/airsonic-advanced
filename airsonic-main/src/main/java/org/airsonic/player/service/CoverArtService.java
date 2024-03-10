package org.airsonic.player.service;

import org.airsonic.player.domain.*;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.repository.CoverArtRepository;
import org.airsonic.player.repository.MediaFileRepository;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Service
@CacheConfig(cacheNames = "coverArtCache")
public class CoverArtService {
    @Autowired
    MediaFolderService mediaFolderService;
    @Autowired
    private CoverArtRepository coverArtRepository;
    @Autowired
    private MediaFileRepository mediaFileRepository;
    @Autowired
    private SecurityService securityService;

    private static final Logger LOG = LoggerFactory.getLogger(CoverArtService.class);

    @CacheEvict(key = "#art.entityType.toString().concat('-').concat(#art.entityId)")
    @Transactional
    public void upsert(CoverArt art) {
        coverArtRepository.save(art);
    }

    public void persistIfNeeded(MediaFile mediaFile) {
        if (mediaFile.getArt() != null && !CoverArt.NULL_ART.equals(mediaFile.getArt())) {
            CoverArt art = get(EntityType.MEDIA_FILE, mediaFile.getId());
            if (CoverArt.NULL_ART.equals(art) || !art.getOverridden()) {
                mediaFile.getArt().setEntityId(mediaFile.getId());
                upsert(mediaFile.getArt());
            }
            mediaFile.setArt(null);
        }
    }

    public void persistIfNeeded(Album album) {
        if (album.getArt() != null && !CoverArt.NULL_ART.equals(album.getArt())) {
            CoverArt art = get(EntityType.ALBUM, album.getId());
            if (CoverArt.NULL_ART.equals(art) || !art.getOverridden()) {
                album.getArt().setEntityId(album.getId());
                upsert(album.getArt());
            }
            album.setArt(null);
        }
    }

    public void persistIfNeeded(Artist artist) {
        if (artist.getArt() != null && !CoverArt.NULL_ART.equals(artist.getArt())) {
            CoverArt art = get(EntityType.ARTIST, artist.getId());
            if (CoverArt.NULL_ART.equals(art) || !art.getOverridden()) {
                artist.getArt().setEntityId(artist.getId());
                upsert(artist.getArt());
            }
            artist.setArt(null);
        }
    }

    @Cacheable(key = "#type.toString().concat('-').concat(#id)", unless = "#result == null") // 'unless' condition should never happen, because of null-object pattern
    public CoverArt get(EntityType type, int id) {
        return coverArtRepository.findByEntityTypeAndEntityId(type, id).orElse(CoverArt.NULL_ART);
    }

    public Path getFullPath(EntityType type, int id) {
        CoverArt art = get(type, id);
        return getFullPath(art);
    }

    public Path getFullPath(CoverArt art) {
        if (art != null && !CoverArt.NULL_ART.equals(art)) {
            if (art.getFolder() == null) {
                // null folder ids mean absolute paths
                return art.getRelativePath();
            } else {
                MusicFolder folder = art.getFolder();
                if (folder != null) {
                    return art.getFullPath(folder.getPath());
                }
            }
        }

        return null;
    }

    @CacheEvict(key = "#type.toString().concat('-').concat(#id)")
    @Transactional
    public void delete(EntityType type, int id) {
        coverArtRepository.deleteByEntityTypeAndEntityId(type, id);
    }

    @CacheEvict(allEntries = true)
    @Transactional
    public void expunge() {
        List<CoverArt> expungeCoverArts = coverArtRepository.findAll().stream()
            .filter(art ->
                (art.getEntityType() == EntityType.ALBUM && art.getAlbum() == null) ||
                (art.getEntityType() == EntityType.ARTIST && art.getArtist() == null) ||
                (art.getEntityType() == EntityType.MEDIA_FILE && art.getMediaFile() == null))
            .collect(Collectors.toList());
        coverArtRepository.deleteAll(expungeCoverArts);
    }

    /**
     * Sets the cover art image for the given media file.
     *
     * @param mediaFileId the media file id
     * @param url         the url of the image
     * @throws Exception if the image could not be saved
     */
    public void setCoverArtImageFromUrl(Integer mediaFileId, String url) throws Exception {

        MediaFile dir = mediaFileRepository.findById(mediaFileId).orElseGet(
            () -> {
                LOG.warn("Could not find media file with id {}", mediaFileId);
                return null;
            });
        saveCoverArt(dir, url);
    }

    private void saveCoverArt(MediaFile dir, String url) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(20 * 1000) // 20 seconds
                .setSocketTimeout(20 * 1000) // 20 seconds
                .build();
        HttpGet method = new HttpGet(url);
        method.setConfig(requestConfig);

        // Attempt to resolve proper suffix.
        String suffix = "jpg";
        if (url.toLowerCase().endsWith(".gif")) {
            suffix = "gif";
        } else if (url.toLowerCase().endsWith(".png")) {
            suffix = "png";
        }

        // Check permissions.
        MusicFolder folder = dir.getFolder();
        Path fullPath = dir.getFullPath();
        Path newCoverFile = fullPath.resolve("cover." + suffix);
        if (!securityService.isWriteAllowed(folder.getPath().relativize(newCoverFile), folder)) {
            throw new SecurityException("Permission denied: " + StringEscapeUtils.escapeHtml(newCoverFile.toString()));
        }

        try (CloseableHttpClient client = HttpClients.createDefault();
                CloseableHttpResponse response = client.execute(method);
                InputStream input = response.getEntity().getContent()) {

            // If file exists, create a backup.
            backupCoverArt(newCoverFile, fullPath.resolve("cover." + suffix + ".backup"));

            // Write file.
            Files.copy(input, newCoverFile, StandardCopyOption.REPLACE_EXISTING);
        }

        CoverArt coverArt = new CoverArt(dir.getId(), EntityType.MEDIA_FILE,
                folder.getPath().relativize(newCoverFile).toString(), dir.getFolder(), true);
        upsert(coverArt);
    }

    private void backupCoverArt(Path newCoverFile, Path backup) {
        if (Files.exists(newCoverFile)) {
            try {
                Files.move(newCoverFile, backup, StandardCopyOption.REPLACE_EXISTING);
                LOG.info("Backed up old image file to {}", backup);
            } catch (IOException e) {
                LOG.warn("Failed to create image file backup {}", backup, e);
            }
        }
    }
}
