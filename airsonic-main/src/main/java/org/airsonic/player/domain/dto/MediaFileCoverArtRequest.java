package org.airsonic.player.domain.dto;

import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;

public class MediaFileCoverArtRequest extends CoverArtRequest {
    /**
     *
     */
    private final MediaFile dir;
    private final Integer proxyId;

    public MediaFileCoverArtRequest(CoverArt coverArt, MediaFile mediaFile, Integer proxyId) {
        super(coverArt,
            () -> mediaFile.getFolder().getId() + "/" + mediaFile.getPath(),
            () -> mediaFile.getChanged());
        this.dir = mediaFile;
        this.proxyId = proxyId;
    }

    public MediaFileCoverArtRequest(CoverArt coverArt, MediaFile mediaFile) {
        this(coverArt, mediaFile, null);
    }

    @Override
    public String getAlbum() {
        return dir.getName();
    }

    @Override
    public String getArtist() {
        return dir.getAlbumArtist() != null ? dir.getAlbumArtist() : dir.getArtist();
    }

    @Override
    public String toString() {
        return "Media file " + dir.getId() + " - " + dir + (proxyId == null ? "" : " (Proxy for " + proxyId + ")");
    }
}