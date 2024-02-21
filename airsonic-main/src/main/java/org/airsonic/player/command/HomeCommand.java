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
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.command;

import org.airsonic.player.controller.HomeController.Album;
import org.airsonic.player.domain.Genre;
import org.airsonic.player.domain.MusicFolder;

import java.util.List;

public class HomeCommand {

    private List<Album> albums;
    private String welcomeTitle;
    private String welcomeSubtitle;
    private String welcomeMessage;
    private boolean isIndexBeingCreated;
    private boolean musicFoldersExist;
    private String listType;
    private int listSize;
    private int coverArtSize;
    private int listOffset;
    private MusicFolder musicFolder;
    private List<Integer> decades;
    private int decade;
    private List<Genre> genres;
    private String genre;
    private boolean keyboardShortcutsEnabled;

    public List<Album> getAlbums() {
        return albums;
    }

    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }

    public String getWelcomeTitle() {
        return welcomeTitle;
    }

    public void setWelcomeTitle(String welcomeTitle) {
        this.welcomeTitle = welcomeTitle;
    }

    public String getWelcomeSubtitle() {
        return welcomeSubtitle;
    }

    public void setWelcomeSubtitle(String welcomeSubtitle) {
        this.welcomeSubtitle = welcomeSubtitle;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public boolean isIndexBeingCreated() {
        return isIndexBeingCreated;
    }

    public void setIndexBeingCreated(boolean indexBeingCreated) {
        isIndexBeingCreated = indexBeingCreated;
    }

    public boolean isMusicFoldersExist() {
        return musicFoldersExist;
    }

    public void setMusicFoldersExist(boolean musicFoldersExist) {
        this.musicFoldersExist = musicFoldersExist;
    }

    public String getListType() {
        return listType;
    }

    public void setListType(String listType) {
        this.listType = listType;
    }

    public int getListSize() {
        return listSize;
    }

    public void setListSize(int listSize) {
        this.listSize = listSize;
    }

    public int getCoverArtSize() {
        return coverArtSize;
    }

    public void setCoverArtSize(int coverArtSize) {
        this.coverArtSize = coverArtSize;
    }

    public int getListOffset() {
        return listOffset;
    }

    public void setListOffset(int listOffset) {
        this.listOffset = listOffset;
    }

    public MusicFolder getMusicFolder() {
        return musicFolder;
    }

    public void setMusicFolder(MusicFolder musicFolder) {
        this.musicFolder = musicFolder;
    }

    public List<Integer> getDecades() {
        return decades;
    }

    public void setDecades(List<Integer> decades) {
        this.decades = decades;
    }

    public int getDecade() {
        return decade;
    }

    public void setDecade(int decade) {
        this.decade = decade;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public void setGenres(List<Genre> genres) {
        this.genres = genres;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public boolean isKeyboardShortcutsEnabled() {
        return keyboardShortcutsEnabled;
    }

    public void setKeyboardShortcutsEnabled(boolean keyboardShortcutsEnabled) {
        this.keyboardShortcutsEnabled = keyboardShortcutsEnabled;
    }

}
