package org.airsonic.player.domain.entity;

import org.airsonic.player.domain.Album;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "starred_album", uniqueConstraints = @UniqueConstraint(columnNames = { "album_id", "username" }))
public class StarredAlbum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "created", nullable = false)
    private Instant created;

    @ManyToOne
    @JoinColumn(name = "album_id")
    private Album album;

    public StarredAlbum() {
    }

    public StarredAlbum(Album album, String username, Instant created) {
        this.username = username;
        this.created = created;
        this.album = album;
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

    public Album getAlbum() {
        return album;
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

    public void setAlbum(Album album) {
        this.album = album;
    }

}
