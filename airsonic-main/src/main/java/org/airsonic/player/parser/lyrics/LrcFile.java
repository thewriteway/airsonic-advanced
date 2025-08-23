package org.airsonic.player.parser.lyrics;

import java.time.Instant;
import java.util.List;

public class LrcFile {

    private final List<LyricsLine> lyricsLines;

    private final Instant lastModified;

    /**
     * Constructor for LrcFile.
     *
     * @param lyricsLines The list of lyrics lines parsed from the LRC file.
     * @param lastModified The last modified timestamp of the LRC file.
     */
    public LrcFile(List<LyricsLine> lyricsLines, Instant lastModified) {
        this.lyricsLines = lyricsLines;
        this.lastModified = lastModified;
    }

    public List<LyricsLine> getLyricsLines() {
        return lyricsLines;
    }

    public Instant getLastModified() {
        return lastModified;
    }
}
