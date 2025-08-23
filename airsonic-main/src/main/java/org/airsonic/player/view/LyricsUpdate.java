package org.airsonic.player.view;

public class LyricsUpdate {

    private Integer id;
    private String lyrics;

    public LyricsUpdate() {
    }

    public LyricsUpdate(Integer id, String lyrics) {
        this.id = id;
        this.lyrics = lyrics;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }
}
