package org.airsonic.player.parser.lyrics;

import jakarta.annotation.Nonnull;

public class LyricsLine {
    private final long time;
    private final String text;

    /**
     * Constructor for LyricsLine.
     * @param time The time in milliseconds when the lyric line should be displayed.
     * @param text The text of the lyric line.
     */
    public LyricsLine(long time, @Nonnull String text) {
        this.time = time;
        this.text = text;
    }

    /**
     * Gets the time of the lyric line.
     * @return The time in milliseconds.
     */
    public long getTime() {
        return time;
    }

    /**
     * Gets the text of the lyric line.
     * @return The text of the lyric line.
     */
    @Nonnull
    public String getText() {
        return text;
    }
}
