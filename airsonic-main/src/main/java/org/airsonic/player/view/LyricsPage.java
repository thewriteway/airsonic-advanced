package org.airsonic.player.view;

public class LyricsPage {

    private final Integer id;
    private final String artist;
    private final String song;
    private final String lyrics;
    private final String source;

    /**
     * Constructor for LyricsPage
     * @param id the mediafile id
     * @param artist the artist name
     * @param song the song title
     */
    public LyricsPage(Integer id, String artist, String song, String lyrics, String source) {
        this.id = id;
        this.artist = artist;
        this.song = song;
        this.lyrics = lyrics;
        this.source = source;
    }

    public Integer getId() {
        return id;
    }

    public String getArtist() {
        return artist;
    }

    public String getSong() {
        return song;
    }

    public String getLyrics() {
        return lyrics;
    }

    public String getSource() {
        return source;
    }

}
