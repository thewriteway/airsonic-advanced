package org.airsonic.player.domain.dto;

import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.util.FileUtil;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class CoverArtRequest {

    /**
     *
     */
    protected CoverArt coverArt;
    protected Supplier<String> keyGenerator;
    protected Supplier<Instant> lastModifiedGenerator;


    CoverArtRequest(CoverArt coverArt, Supplier<String> keyGenerator, Supplier<Instant> lastModifiedGenerator) {
        this.coverArt = CoverArt.NULL_ART.equals(coverArt) ? null : coverArt;
        this.keyGenerator = keyGenerator;
        this.lastModifiedGenerator = lastModifiedGenerator;
    }

    public String getKey() {
        return Optional.ofNullable(coverArt).map(c -> coverArt.getFolder()).map(folder -> folder.getId() + "/" + coverArt.getPath())
                .orElseGet(keyGenerator);
    }

    public Instant lastModified() {
        return Optional.ofNullable(coverArt).map(c -> FileUtil.lastModified(c.getFullPath()))
                .orElseGet(lastModifiedGenerator);
    }

    public CoverArt getCoverArt() {
        return coverArt;
    }

    public abstract String getAlbum();

    public abstract String getArtist();
}