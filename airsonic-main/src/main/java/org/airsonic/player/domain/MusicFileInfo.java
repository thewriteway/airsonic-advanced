package org.airsonic.player.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "music_file_info")
public class MusicFileInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "comment")
    private String comment;

    @Column(name = "play_count")
    private Integer playCount;

    @Column(name = "last_played")
    private Instant lastPlayed;

    @Column(name = "enabled")
    private Boolean enabled = true;

    public MusicFileInfo() {
    }

    public MusicFileInfo(String path, Integer rating, String comment, Integer playCount, Instant lastPlayed,
            Boolean enabled) {
        this.path = path;
        this.rating = rating;
        this.comment = comment;
        this.playCount = playCount;
        this.lastPlayed = lastPlayed;
        this.enabled = enabled;
    }

    public Integer getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Integer getPlayCount() {
        return playCount;
    }

    public Instant getLastPlayed() {
        return lastPlayed;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setPlayCount(Integer playCount) {
        this.playCount = playCount;
    }

    public void setLastPlayed(Instant lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

}
