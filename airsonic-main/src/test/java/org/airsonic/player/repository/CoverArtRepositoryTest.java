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

package org.airsonic.player.repository;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
@Transactional
public class CoverArtRepositoryTest {

    @Autowired
    private CoverArtRepository coverArtRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    static void init() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @Test
    void testDeleteOrphanedKeepsArtOfPresentEntities() {
        MusicFolder folder = musicFolderRepository.save(new MusicFolder(Paths.get("coverArtTest"), "coverArtTest", Type.MEDIA, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS)));
        Album present = albumRepository.save(new Album("present", "present", "artist", Instant.now(), Instant.now(), true, folder));
        Album gone = albumRepository.save(new Album("gone", "gone", "artist", Instant.now(), Instant.now(), false, folder));
        int missingId = Math.max(present.getId(), gone.getId()) + 1000;

        coverArtRepository.save(new CoverArt(present.getId(), EntityType.ALBUM, "present.jpg", folder, false));
        coverArtRepository.save(new CoverArt(gone.getId(), EntityType.ALBUM, "gone.jpg", folder, false));
        coverArtRepository.save(new CoverArt(missingId, EntityType.ARTIST, "artist.jpg", folder, false));
        coverArtRepository.save(new CoverArt(missingId, EntityType.MEDIA_FILE, "file.jpg", folder, false));
        coverArtRepository.flush();

        // the shared test database may hold orphans of other tests as well
        assertTrue(coverArtRepository.deleteOrphaned(EntityType.ALBUM, EntityType.ARTIST, EntityType.MEDIA_FILE) >= 3);

        assertTrue(coverArtRepository.findByEntityTypeAndEntityId(EntityType.ALBUM, present.getId()).isPresent());
        assertFalse(coverArtRepository.findByEntityTypeAndEntityId(EntityType.ALBUM, gone.getId()).isPresent());
        assertFalse(coverArtRepository.findByEntityTypeAndEntityId(EntityType.ARTIST, missingId).isPresent());
        assertFalse(coverArtRepository.findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, missingId).isPresent());
    }
}
