/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.domain.entity;

import org.airsonic.player.domain.Artist;

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
@Table(name = "starred_artist", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "username", "artist_id" })
})
public class StarredArtist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    private String username;

    private Instant created;

    public StarredArtist() {
    }

    public StarredArtist(Artist artist, String username, Instant created) {
        this.artist = artist;
        this.username = username;
        this.created = created;
    }

    public StarredArtist(Integer id, String username, Artist artist, Instant created) {
        this.id = id;
        this.username = username;
        this.artist = artist;
        this.created = created;
    }

    public Integer getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Artist getArtist() {
        return artist;
    }

    public Instant getCreated() {
        return created;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setArtist(Artist artist) {
        this.artist = artist;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

}
