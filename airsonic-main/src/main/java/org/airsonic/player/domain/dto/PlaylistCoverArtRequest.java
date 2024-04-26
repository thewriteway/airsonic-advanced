package org.airsonic.player.domain.dto;

import org.airsonic.player.controller.CoverArtController;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.Playlist;

public class PlaylistCoverArtRequest extends CoverArtRequest {

    private final Playlist playlist;

    public PlaylistCoverArtRequest(CoverArt coverArt, Playlist playlist) {
        super(coverArt, () -> CoverArtController.PLAYLIST_COVERART_PREFIX + playlist.getId(), () -> playlist.getChanged());
        this.playlist = playlist;
    }

    @Override
    public String getAlbum() {
        return null;
    }

    @Override
    public String getArtist() {
        return playlist.getName();
    }

    @Override
    public String toString() {
        return "Playlist " + playlist.getId() + " - " + playlist.getName();
    }

    public Playlist getPlaylist() {
        return playlist;
    }

}