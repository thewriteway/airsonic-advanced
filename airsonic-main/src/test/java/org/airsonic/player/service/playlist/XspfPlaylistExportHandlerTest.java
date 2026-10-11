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
 */
package org.airsonic.player.service.playlist;

import chameleon.playlist.xspf.Track;
import chameleon.playlist.xspf.XspfProvider;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.PlaylistMediaFile;
import org.airsonic.player.repository.PlaylistRepository;
import org.airsonic.player.service.CoverArtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class XspfPlaylistExportHandlerTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private CoverArtService coverArtService;

    @InjectMocks
    private XspfPlaylistExportHandler handler;

    private final MusicFolder folder = new MusicFolder(1, Paths.get("music"), "Music", MusicFolder.Type.MEDIA, true, Instant.now());

    private MediaFile song(int id, Double durationSeconds) {
        MediaFile file = new MediaFile();
        file.setId(id);
        file.setFolder(folder);
        file.setPath("song" + id + ".mp3");
        file.setTitle("Song " + id);
        file.setDuration(durationSeconds);
        return file;
    }

    @Test
    public void testMissingPlaylistExportsEmptyPlaylist() {
        when(playlistRepository.findById(99)).thenReturn(Optional.empty());

        chameleon.playlist.xspf.Playlist result = (chameleon.playlist.xspf.Playlist) handler.handle(99, new XspfProvider());

        assertTrue(result.getTracks().isEmpty());
    }

    @Test
    public void testDurationsAreExportedInMillisecondsAndSkippedWhenUnknown() {
        Playlist playlist = new Playlist();
        playlist.setName("Mix");
        playlist.setUsername("admin");
        playlist.setPlaylistMediaFiles(List.of(
                new PlaylistMediaFile(playlist, song(1, 215.4), 0),
                new PlaylistMediaFile(playlist, song(2, null), 1),
                new PlaylistMediaFile(playlist, song(3, 0.0), 2)));
        when(playlistRepository.findById(7)).thenReturn(Optional.of(playlist));

        chameleon.playlist.xspf.Playlist result = (chameleon.playlist.xspf.Playlist) handler.handle(7, new XspfProvider());

        assertEquals("Mix", result.getTitle());
        List<Track> tracks = result.getTracks();
        assertEquals(3, tracks.size());
        assertEquals(215400, tracks.get(0).getDuration());
        // XSPF requires a strictly positive duration, so unknown and zero durations are left out
        assertNull(tracks.get(1).getDuration());
        assertNull(tracks.get(2).getDuration());
    }
}
