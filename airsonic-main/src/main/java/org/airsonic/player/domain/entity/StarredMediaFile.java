package org.airsonic.player.domain.entity;

import org.airsonic.player.domain.MediaFile;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "starred_media_file", uniqueConstraints = @UniqueConstraint(columnNames = { "media_file_id",
    "username" }))
public class StarredMediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "created", nullable = false)
    private Instant created;

    @ManyToOne
    @JoinColumn(name = "media_file_id", referencedColumnName = "id")
    private MediaFile mediaFile;

    public StarredMediaFile() {
    }

    public StarredMediaFile(MediaFile mediaFile, String username, Instant created) {
        this.username = username;
        this.created = created;
        this.mediaFile = mediaFile;
    }

    public Integer getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Instant getCreated() {
        return created;
    }

    public MediaFile getMediaFile() {
        return mediaFile;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public void setMediaFile(MediaFile mediaFile) {
        this.mediaFile = mediaFile;
    }

}
