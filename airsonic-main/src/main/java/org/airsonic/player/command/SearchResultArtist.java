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

import org.airsonic.player.domain.MusicFolder;

import java.util.HashSet;
import java.util.Set;

public class SearchResultArtist {

    private String artist;

    private MusicFolder folder;

    private Set<Integer> mediaFileIds = new HashSet<>();

    public SearchResultArtist(String artist, MusicFolder folder) {
        this.artist = artist;
        this.folder = folder;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public MusicFolder getFolder() {
        return folder;
    }

    public void setFolder(MusicFolder folder) {
        this.folder = folder;
    }

    public Set<Integer> getMediaFileIds() {
        return mediaFileIds;
    }

    public void setMediaFileIds(Set<Integer> mediaFileIds) {
        this.mediaFileIds = mediaFileIds;
    }

    public void addMediaFileId(Integer mediaFileId) {
        this.mediaFileIds.add(mediaFileId);
    }

    public void addMediaFileIds(Set<Integer> mediaFileIds) {
        this.mediaFileIds.addAll(mediaFileIds);
    }

}
