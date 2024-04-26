package org.airsonic.player.domain.dto;

import org.airsonic.player.domain.MediaFile;

public class VideoCoverArtRequest extends CoverArtRequest {

    private final MediaFile mediaFile;
    private final int offset;

    public VideoCoverArtRequest(MediaFile mediaFile, int offset) {
        super(null,
                () -> mediaFile.getFolder().getId() + "/" + mediaFile.getPath() + "/" + offset,
                () -> mediaFile.getChanged());
        this.mediaFile = mediaFile;
        this.offset = offset;
    }

    @Override
    public String getAlbum() {
        return null;
    }

    @Override
    public String getArtist() {
        return mediaFile.getName();
    }

    @Override
    public String toString() {
        return "Video file " + mediaFile.getId() + " - " + mediaFile;
    }

    public MediaFile getMediaFile() {
        return mediaFile;
    }

    public int getOffset() {
        return offset;
    }
}