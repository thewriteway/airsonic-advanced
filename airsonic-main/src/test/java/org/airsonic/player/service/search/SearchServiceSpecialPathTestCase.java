
package org.airsonic.player.service.search;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.util.ObjectUtils.isEmpty;

/*
 * Test cases related to #1139.
 * Confirming whether shuffle search can be performed correctly in MusicFolder containing special strings.
 *
 * (Since the query of getRandomAlbums consists of folder paths only,
 * this verification is easy to perform.)
 *
 * This test case is a FalsePattern for search,
 * but there may be problems with the data flow prior to creating the search index.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableConfigurationProperties
public class SearchServiceSpecialPathTestCase {

    private List<MusicFolder> musicFolders;

    @Autowired
    private SearchService searchService;

    private List<MusicFolder> getMusicFolders() {
        if (isEmpty(musicFolders)) {
            musicFolders = new ArrayList<>();

            Path musicDir = MusicFolderTestData.resolveBaseMediaPath().resolve("Search").resolve("SpecialPath").resolve("accessible");
            musicFolders.add(new MusicFolder(1, musicDir, "accessible", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS)));

            Path music2Dir = MusicFolderTestData.resolveBaseMediaPath().resolve("Search").resolve("SpecialPath").resolve("accessible's");
            musicFolders.add(new MusicFolder(2, music2Dir, "accessible's", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS)));

            Path music3Dir = MusicFolderTestData.resolveBaseMediaPath().resolve("Search").resolve("SpecialPath").resolve("accessible+s");
            musicFolders.add(new MusicFolder(3, music3Dir, "accessible+s", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS)));
        }
        return musicFolders;
    }

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private MediaScannerService mediaScannerService;

    @TempDir
    private static Path airsonicHome;

    @BeforeAll
    public static void setupAll() {
        System.setProperty("airsonic.home", airsonicHome.toString());
    }

    @BeforeEach
    public void setup() {
        for (MusicFolder musicFolder : getMusicFolders()) {
            mediaFolderService.createMusicFolder(musicFolder);
        }
        TestCaseUtils.execScan(mediaScannerService);
    }

    @AfterEach
    public void tearDown() {
        for (MusicFolder musicFolder : getMusicFolders()) {
            mediaFolderService.deleteMusicFolder(musicFolder.getId());
        }
        musicFolders.clear();
        mediaFolderService.expunge();
    }

    @Test
    public void testSpecialCharactersInDirName() {

        List<MusicFolder> folders = getMusicFolders();

        // ALL Songs
        List<MediaFile> randomAlbums = searchService.getRandomAlbums(Integer.MAX_VALUE, folders);
        assertEquals(3, randomAlbums.size(), "ALL Albums ");

        // dir - accessible
        List<MusicFolder> folder01 = folders.stream()
                .filter(m -> "accessible".equals(m.getName()))
                .collect(Collectors.toList());
        randomAlbums = searchService.getRandomAlbums(Integer.MAX_VALUE, folder01);
        assertEquals(1, randomAlbums.size(), "Albums in \"accessible\" ");

        // dir - accessible's
        List<MusicFolder> folder02 = folders.stream()
                .filter(m -> "accessible's".equals(m.getName()))
                .collect(Collectors.toList());
        randomAlbums = searchService.getRandomAlbums(Integer.MAX_VALUE, folder02);
        assertEquals(1, randomAlbums.size(), "Albums in \"accessible's\" ");

        // dir - accessible+s
        List<MusicFolder> folder03 = folders.stream()
                .filter(m -> "accessible+s".equals(m.getName()))
                .collect(Collectors.toList());
        randomAlbums = searchService.getRandomAlbums(Integer.MAX_VALUE, folder03);
        assertEquals(1, folder03.size(), "Albums in \"accessible+s\" ");

    }

}
