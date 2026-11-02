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
package org.airsonic.player.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-imported playlists are identified by their path in the playlist folder, so same-named files in different
 * subfolders become different playlists.
 */
public class PlaylistFileServiceImportKeyTest {

    @TempDir
    Path playlistFolder;

    @Test
    public void topLevelFilesAreKeyedByTheirName() {
        assertThat(PlaylistFileService.getImportKey(playlistFolder, playlistFolder.resolve("favorites.m3u")))
                .isEqualTo("favorites.m3u");
    }

    @Test
    public void filesInSubfoldersAreKeyedByTheirRelativePath() {
        String rock = PlaylistFileService.getImportKey(playlistFolder, playlistFolder.resolve("rock").resolve("favorites.m3u"));
        String jazz = PlaylistFileService.getImportKey(playlistFolder, playlistFolder.resolve("jazz").resolve("favorites.m3u"));

        assertThat(rock).isEqualTo("rock/favorites.m3u");
        assertThat(jazz).isEqualTo("jazz/favorites.m3u");
    }

    @Test
    public void filesOutsideThePlaylistFolderAreKeyedByTheirName() {
        assertThat(PlaylistFileService.getImportKey(playlistFolder.resolve("sub"), playlistFolder.resolve("other.m3u")))
                .isEqualTo("other.m3u");
    }
}
