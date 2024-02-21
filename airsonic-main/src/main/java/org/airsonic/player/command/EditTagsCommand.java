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

 Copyright 2024 (C) Y.Tory
 */
package org.airsonic.player.command;

import org.airsonic.player.controller.EditTagsController.Song;

import java.util.List;
import java.util.SortedSet;

public class EditTagsCommand {

    private String defaultArtist;
    private String defaultAlbum;
    private String defaultGenre;
    private Integer defaultYear;
    private SortedSet<String> allGenres;
    private Integer id;
    private List<Song> songs;

    public String getDefaultArtist() {
        return defaultArtist;
    }

    public void setDefaultArtist(String defaultArtist) {
        this.defaultArtist = defaultArtist;
    }

    public String getDefaultAlbum() {
        return defaultAlbum;
    }

    public void setDefaultAlbum(String defaultAlbum) {
        this.defaultAlbum = defaultAlbum;
    }

    public String getDefaultGenre() {
        return defaultGenre;
    }

    public void setDefaultGenre(String defaultGenre) {
        this.defaultGenre = defaultGenre;
    }

    public Integer getDefaultYear() {
        return defaultYear;
    }

    public void setDefaultYear(Integer defaultYear) {
        this.defaultYear = defaultYear;
    }

    public SortedSet<String> getAllGenres() {
        return allGenres;
    }

    public void setAllGenres(SortedSet<String> allGenres) {
        this.allGenres = allGenres;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

}
