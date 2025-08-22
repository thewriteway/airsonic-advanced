package org.airsonic.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "lyrics")
public class Lyrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "lyrics", nullable = false)
    private String lyrics;

    @Column(name = "media_file_id", nullable = false)
    private Integer mediaFileId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "created", nullable = false)
    private Instant created;

    @Column(name = "updated", nullable = false)
    private Instant updated;

    /**
     * Creates a new Lyrics instance with the provided lyrics.
     * The created and updated timestamps are set to the current time.
     * The timestamps are truncated to microseconds for consistency.
     *
     * @param lyrics The lyrics text.
     */
    public Lyrics(String lyrics, Integer mediaFileId, String source) {
        this.lyrics = lyrics;
        this.mediaFileId = mediaFileId;
        this.source = source;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.created = now;
        this.updated = now;
    }

    public Lyrics() {
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

    public Integer getMediaFileId() {
        return mediaFileId;
    }

    public void setMediaFileId(Integer mediaFileId) {
        this.mediaFileId = mediaFileId;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getUpdated() {
        return updated;
    }

    public void setUpdated(Instant updated) {
        this.updated = updated;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public int hashCode() {
        return lyrics != null ? lyrics.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Lyrics other = (Lyrics) obj;
        return lyrics != null ? lyrics.equals(other.getLyrics()) : other.getLyrics() == null;
    }

}
