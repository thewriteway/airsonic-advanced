package org.airsonic.player.service;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.io.Resources;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.Player;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.subsonic.restapi.Child;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * A unit test class to test the MediaScannerService.
 * <p>
 * This class uses the Spring application context configuration present in the
 * /org/airsonic/player/service/mediaScannerServiceTestCase/ directory.
 * <p>
 * The media library is found in the /MEDIAS directory.
 * It is composed of 2 musicFolders (Music and Music2) and several little weight audio files.
 * <p>
 * At runtime, the subsonic_home dir is set to target/test-classes/org/airsonic/player/service/mediaScannerServiceTestCase.
 * An empty database is created on the fly.
 */
@TestPropertySource(properties = {
    "airsonic.cue.enabled=true"
})
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class MediaScannerServiceTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(MediaScannerServiceTestCase.class);

    @TempDir
    private static Path tempDir;

    private final MetricRegistry metrics = new MetricRegistry();

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @MockitoSpyBean
    private MediaFileService mediaFileService;

    @Autowired
    private ArtistService artistService;

    @Autowired
    private AlbumService albumService;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private JaxbContentService jaxbContentService;

    @Autowired
    private RatingService ratingService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private SettingsService settingsService;

    @TempDir
    private Path temporaryFolder;

    private List<MusicFolder> testFolders = new ArrayList<>();

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("DELETE FROM media_file");
        jdbcTemplate.execute("DELETE FROM album");
        jdbcTemplate.execute("DELETE FROM artist");
        TestCaseUtils.waitForScanFinish(mediaScannerService);
        mediaFolderService.clearMusicFolderCache();
        mediaFolderService.clearMediaFileCache();
        testFolders = new ArrayList<>();
    }

    @AfterEach
    public void cleanup() {
        testFolders.forEach(f -> musicFolderRepository.delete(f));
        testFolders.clear();
    }

    /**
     * Tests the MediaScannerService by scanning the test media library into an empty database.
     */
    @Test
    public void testScanLibrary() {
        LOG.info("start testScanLibrary");
        Timer globalTimer = metrics.timer(MetricRegistry.name(MediaScannerServiceTestCase.class, "Timer.global"));

        Timer.Context globalTimerContext = globalTimer.time();
        testFolders = MusicFolderTestData.getTestMusicFolders();
        musicFolderRepository.saveAll(testFolders);
        mediaFolderService.clearMusicFolderCache();
        TestCaseUtils.execScan(mediaScannerService);

        globalTimerContext.stop();

        // Music Folder Music must have 3 children
        List<MediaFile> listeMusicChildren = mediaFileRepository.findByFolderAndParentPath(testFolders.get(0), "", Sort.by("startPosition"));
        assertEquals(3, listeMusicChildren.size());
        // Music Folder Music2 must have 1 children
        List<MediaFile> listeMusic2Children = mediaFileRepository.findByFolderAndParentPath(testFolders.get(1), "", Sort.by("startPosition"));
        assertEquals(1, listeMusic2Children.size());

        System.out.println("--- List of all artists ---");
        System.out.println("artistName#albumCount");
        List<Artist> allArtists = artistService.getAlphabeticalArtists(testFolders);
        allArtists.forEach(artist -> System.out.println(artist.getName() + "#" + artist.getAlbumCount()));
        System.out.println("--- *********************** ---");

        System.out.println("--- List of all albums ---");
        System.out.println("name#artist");
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, testFolders);
        allAlbums.forEach(album -> System.out.println(album.getName() + "#" + album.getArtist()));
        assertEquals(5, allAlbums.size());
        System.out.println("--- *********************** ---");

        List<MediaFile> listeSongs = mediaFileService.getSongsByGenre(0, Integer.MAX_VALUE, "Baroque Instrumental", testFolders);
        assertEquals(2, listeSongs.size());

        // display out metrics report
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.report();

        System.out.print("End");
    }


    /**
     * The bulk album builder used by getAlbumList2 and other album lists must give the same result as building
     * each album on its own, against a real database.
     */
    @Test
    public void testBulkAlbumConversionMatchesPerAlbumConversion() {
        testFolders = MusicFolderTestData.getTestMusicFolders();
        musicFolderRepository.saveAll(testFolders);
        mediaFolderService.clearMusicFolderCache();
        TestCaseUtils.execScan(mediaScannerService);

        List<Album> albums = albumService.getAlphabeticalAlbums(true, true, testFolders);
        assertEquals(5, albums.size());
        albumService.starOrUnstar(albums.get(0).getId(), "admin", true);

        List<org.subsonic.restapi.AlbumID3> bulk = jaxbContentService.createJaxbAlbums(albums, "admin");

        assertEquals(albums.size(), bulk.size());
        assertNotNull(bulk.get(0).getStarred());
        for (int i = 0; i < albums.size(); i++) {
            org.subsonic.restapi.AlbumID3 single = jaxbContentService.createJaxbAlbum(
                    new org.subsonic.restapi.AlbumID3(), albums.get(i), "admin");
            org.subsonic.restapi.AlbumID3 fromBulk = bulk.get(i);
            assertEquals(single.getId(), fromBulk.getId());
            assertEquals(single.getName(), fromBulk.getName());
            assertEquals(single.getArtist(), fromBulk.getArtist());
            assertEquals(single.getArtistId(), fromBulk.getArtistId(), "artistId of " + single.getName());
            assertEquals(single.getCoverArt(), fromBulk.getCoverArt(), "coverArt of " + single.getName());
            assertEquals(single.getStarred(), fromBulk.getStarred(), "starred of " + single.getName());
        }
        assertTrue(bulk.stream().anyMatch(a -> a.getArtistId() != null), "some albums should resolve their artist");
    }

    @Test
    public void testBulkChildConversionMatchesPerChildConversion() {
        testFolders = MusicFolderTestData.getTestMusicFolders();
        musicFolderRepository.saveAll(testFolders);
        mediaFolderService.clearMusicFolderCache();
        TestCaseUtils.execScan(mediaScannerService);

        List<MediaFile> songs = mediaFileService.getSongs(testFolders, 1000, 0);
        assertTrue(songs.size() > 1, "the test library should have songs");
        // songs plus their album directories, so both kinds of entry are compared
        List<MediaFile> entries = Stream.concat(songs.stream(),
                songs.stream().map(mediaFileService::getParentOf).filter(Objects::nonNull).distinct()).toList();
        mediaFileService.starMediaFiles(List.of(songs.get(0).getId()), "admin");
        ratingService.setRatingForUser("admin", songs.get(1), 4);
        Player player = new Player();
        player.setUsername("admin");
        player = playerService.createPlayer(player);

        List<Child> bulk = jaxbContentService.createJaxbChildren(player, entries, "admin");

        assertEquals(entries.size(), bulk.size());
        assertNotNull(bulk.get(0).getStarred());
        assertEquals(4, bulk.get(1).getUserRating());
        assertEquals(4.0, bulk.get(1).getAverageRating());
        for (int i = 0; i < entries.size(); i++) {
            Child single = jaxbContentService.createJaxbChild(player, entries.get(i), "admin");
            Child fromBulk = bulk.get(i);
            String what = " of " + entries.get(i).getPath();
            assertEquals(single.getId(), fromBulk.getId());
            assertEquals(single.isIsDir(), fromBulk.isIsDir(), "isDir" + what);
            assertEquals(single.getParent(), fromBulk.getParent(), "parent" + what);
            assertEquals(single.getCoverArt(), fromBulk.getCoverArt(), "coverArt" + what);
            assertEquals(single.getStarred(), fromBulk.getStarred(), "starred" + what);
            assertEquals(single.getUserRating(), fromBulk.getUserRating(), "userRating" + what);
            assertEquals(single.getAverageRating(), fromBulk.getAverageRating(), "averageRating" + what);
            assertEquals(single.getAlbumId(), fromBulk.getAlbumId(), "albumId" + what);
            assertEquals(single.getArtistId(), fromBulk.getArtistId(), "artistId" + what);
            assertEquals(single.getType(), fromBulk.getType(), "type" + what);
            assertEquals(single.getTranscodedSuffix(), fromBulk.getTranscodedSuffix(), "transcodedSuffix" + what);
        }
        assertTrue(bulk.stream().anyMatch(c -> c.getAlbumId() != null), "some songs should resolve their album");
        assertTrue(bulk.stream().anyMatch(c -> c.getArtistId() != null), "some songs should resolve their artist");
        assertTrue(bulk.stream().anyMatch(c -> c.getParent() != null), "some entries should have a parent");
    }

    @Test
    public void testBulkArtistConversionMatchesPerArtistConversion() {
        testFolders = MusicFolderTestData.getTestMusicFolders();
        musicFolderRepository.saveAll(testFolders);
        mediaFolderService.clearMusicFolderCache();
        TestCaseUtils.execScan(mediaScannerService);

        // artists as id3 entries (search3, getStarred2, getArtistInfo2)
        List<Artist> artists = artistService.getArtists(testFolders, 100, 0);
        assertTrue(artists.size() > 1, "the test library should have artists");
        artistService.starOrUnstar(artists.get(0).getId(), "admin", true);
        List<org.subsonic.restapi.ArtistID3> bulkId3 = jaxbContentService.createJaxbArtistsID3(artists, "admin");
        assertEquals(artists.size(), bulkId3.size());
        assertNotNull(bulkId3.get(0).getStarred());
        for (int i = 0; i < artists.size(); i++) {
            org.subsonic.restapi.ArtistID3 single = jaxbContentService.createJaxbArtist(
                    new org.subsonic.restapi.ArtistID3(), artists.get(i), "admin");
            assertEquals(single.getId(), bulkId3.get(i).getId());
            assertEquals(single.getName(), bulkId3.get(i).getName());
            assertEquals(single.getStarred(), bulkId3.get(i).getStarred(), "starred of " + single.getName());
            assertEquals(single.getCoverArt(), bulkId3.get(i).getCoverArt(), "coverArt of " + single.getName());
        }

        // directories as folder entries (getIndexes shortcuts, search2, getStarred, getArtistInfo)
        List<MediaFile> dirs = mediaFileService.getSongs(testFolders, 1000, 0).stream()
                .map(mediaFileService::getParentOf).filter(Objects::nonNull).distinct().toList();
        assertTrue(dirs.size() > 1, "the test library should have directories");
        mediaFileService.starMediaFiles(List.of(dirs.get(0).getId()), "admin");
        ratingService.setRatingForUser("admin", dirs.get(1), 3);
        List<org.subsonic.restapi.Artist> bulkFolders = jaxbContentService.createJaxbArtists(dirs, "admin");
        assertNotNull(bulkFolders.get(0).getStarred());
        assertEquals(3, bulkFolders.get(1).getUserRating());
        for (int i = 0; i < dirs.size(); i++) {
            org.subsonic.restapi.Artist single = jaxbContentService.createJaxbArtist(dirs.get(i), "admin");
            assertEquals(single.getId(), bulkFolders.get(i).getId());
            assertEquals(single.getName(), bulkFolders.get(i).getName());
            assertEquals(single.getStarred(), bulkFolders.get(i).getStarred(), "starred of " + single.getName());
            assertEquals(single.getUserRating(), bulkFolders.get(i).getUserRating(), "userRating of " + single.getName());
            assertEquals(single.getAverageRating(), bulkFolders.get(i).getAverageRating(), "averageRating of " + single.getName());
        }

        // the whole-library lookups getIndexes uses
        assertEquals(mediaFileService.getMediaFileStarredDate(dirs.get(0), "admin"),
                mediaFileService.getMediaFileStarredDates("admin").get(dirs.get(0).getId()));
        assertEquals(3, ratingService.getRatingsForUser("admin").get(dirs.get(1).getId()));
        assertEquals(3.0, ratingService.getAverageRatings().get(dirs.get(1).getId()));
    }

    @Test
    public void testSpecialCharactersInFilename() throws Exception {
        LOG.info("start testSpecialCharactersInFilename");
        String directoryName = "Muff1nman\u2019s \uFF0FMusic";
        String fileName = "Muff1nman\u2019s\uFF0FPiano.mp3";
        Path artistDir = temporaryFolder.resolve(directoryName);
        Path musicFile = artistDir.resolve(fileName);
        Files.createDirectories(artistDir);
        Files.copy(Paths.get(Resources.getResource("MEDIAS/piano.mp3").toURI()), musicFile);

        MusicFolder musicFolder = new MusicFolder(temporaryFolder, "MusicSpecial", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        MediaFile mediaFile = mediaFileService.getMediaFile(musicFile);
        assertEquals(mediaFile.getRelativePath(), temporaryFolder.relativize(musicFile));
        assertThat(mediaFile.getFolder().getId()).isEqualTo(musicFolder.getId());
        MediaFile relativeMediaFile = mediaFileService.getMediaFile(temporaryFolder.relativize(musicFile), musicFolder);
        assertEquals(relativeMediaFile.getRelativePath(), mediaFile.getRelativePath());
    }

    @Test
    public void testNeverScanned() {
        LOG.info("start testNeverScanned");
        mediaScannerService.neverScanned();
    }

    @Test
    public void testMusicBrainzReleaseIdTag() {
        LOG.info("start testMusicBrainzReleaseIdTag");

        // Add the "Music3" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusic3FolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "Music3", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Music3" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(1, allArtists.size());
        Artist artist = allArtists.get(0);
        assertEquals("TestMusic3Artist", artist.getName());
        assertEquals(1, artist.getAlbumCount());

        // Test that the album is correctly imported, along with its MusicBrainz release ID
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("TestAlbum", album.getName());
        assertEquals("TestMusic3Artist", album.getArtist());
        assertEquals(1, album.getSongCount());
        assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", album.getMusicBrainzReleaseId());
        assertEquals("TestAlbum", album.getPath());

        // Test that the music file is correctly imported, along with its MusicBrainz release ID and recording ID
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("Aria", file.getTitle());
        assertEquals("flac", file.getFormat());
        assertEquals("TestAlbum", file.getAlbumName());
        assertEquals("TestMusic3Artist", file.getArtist());
        assertEquals("TestMusic3Artist", file.getAlbumArtist());
        assertEquals(1, (long)file.getTrackNumber());
        assertEquals(2001, (long)file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("01 - Aria.flac").toString(), file.getPath());
        assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", file.getMusicBrainzReleaseId());
        assertEquals("831586f4-56f9-4785-ac91-447ae20af633", file.getMusicBrainzRecordingId());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);
    }

    @Test
    public void testMusicCue() {
        LOG.info("start testMusicCue");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicCueFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "Cue", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(1, allArtists.size());
        Artist artist = allArtists.get(0);
        assertEquals("TestCueArtist", artist.getName());
        assertEquals(1, artist.getAlbumCount());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("AirsonicTest", album.getName());
        assertEquals("TestCueArtist", album.getArtist());
        assertEquals(2, album.getSongCount());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(3, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), file.getPath());
        assertTrue(file.getIndexPath().contains("airsonic-test.cue"));
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

        MediaFile track1 = albumFiles.get(1);
        assertEquals("Handel", track1.getTitle());
        assertEquals("wav", track1.getFormat());
        assertEquals(track1.getAlbumName(), "AirsonicTest");
        assertEquals("Beecham", track1.getArtist());
        assertEquals("TestCueArtist", track1.getAlbumArtist());
        assertEquals(1L, (long)track1.getTrackNumber());
        assertNull(track1.getYear());
        assertEquals(album.getPath(), track1.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), track1.getPath());
        assertNull(track1.getIndexPath());
        assertEquals(0.0d, track1.getStartPosition(), 0.0d);
    }

    @Test
    public void testMusicCueWithBOM() {
        LOG.info("start testMusicCueWithBOM");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicCueWithBOMFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "Cue", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(1, allArtists.size());
        Artist artist = allArtists.get(0);
        assertEquals("TestCueArtist", artist.getName());
        assertEquals(1, artist.getAlbumCount());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("AirsonicTest", album.getName());
        assertEquals("TestCueArtist", album.getArtist());
        assertEquals(2, album.getSongCount());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(3, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), file.getPath());
        assertTrue(file.getIndexPath().contains("airsonic-test.cue"));
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

        MediaFile track1 = albumFiles.get(1);
        assertEquals("Handel", track1.getTitle());
        assertEquals("wav", track1.getFormat());
        assertEquals(track1.getAlbumName(), "AirsonicTest");
        assertEquals("Beecham", track1.getArtist());
        assertEquals("TestCueArtist", track1.getAlbumArtist());
        assertEquals(1L, (long)track1.getTrackNumber());
        assertNull(track1.getYear());
        assertEquals(album.getPath(), track1.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), track1.getPath());
        assertNull(track1.getIndexPath());
        assertEquals(0.0d, track1.getStartPosition(), 0.0d);
    }

    @Test
    public void testMusicCueWithDisableCueIndexing() {
        LOG.info("start testMusicCueWithDisableCueIndexing");

        when(settingsService.getEnableCueIndexing()).thenReturn(false);

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicDisableCueFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "CueDisabled", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(0, allArtists.size());

        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(0, allAlbums.size());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(musicFolder, "", Sort.by("startPosition"));
        assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals("", file.getParentPath());
        assertEquals("airsonic-test.wav", file.getPath());
        assertNull(file.getIndexPath());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

    }



    @Test
    public void testMusicInvalidCueWithLengthError() {
        LOG.info("start testMusicInvalidCueWithLengthError");

        when(settingsService.getEnableCueIndexing()).thenReturn(true);

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicInvalidCue2FolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "InvalidCue2", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(0, allArtists.size());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(0, allAlbums.size());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(folders.get(0), "", Sort.by("startPosition"));
        assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals("", file.getParentPath());
        assertEquals("airsonic-test.wav", file.getPath());
        assertNull(file.getIndexPath());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);
    }


    @Test
    public void testMusicInvalidCueWithWarning() {
        LOG.info("start testMusicInvalidCueWithWarning");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicInvalidCue3FolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "InvalidCue3", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(0, allArtists.size());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(0, allAlbums.size());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(folders.get(0), "", Sort.by("startPosition"));
        assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals("", file.getParentPath());
        assertEquals("airsonic-test.wav", file.getPath());
        assertNull(file.getIndexPath());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);
    }




    @Test
    public void testMusicWithCommmaFolderAndDuplicateBasenameAudio() {
        LOG.info("start testMusicWithCommmaFolderAndDuplicateBasenameAudio");
        // Add the "Music4" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusic4FolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "Music4", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Music4" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        List<MediaFile> listMusicChildren = mediaFileRepository.findByFolderAndParentPath(musicFolder, "", Sort.by("startPosition"));
        assertEquals(2, listMusicChildren.size());

        List<MediaFile> listDuplicateBaseNameFiles = mediaFileRepository.findByFolderAndParentPath(musicFolder, "a", Sort.by("startPosition"));
        assertEquals(2, listDuplicateBaseNameFiles.size());


    }

    @Test
    public void testMpcAudioTest() {
        LOG.info("start testMpcAudioTest");

        // Add the "MusicMpc" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicMpcFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "mpc", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Music4" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        List<MediaFile> listMusicChildren = mediaFileRepository.findByFolderAndParentPath(musicFolder, "", Sort.by("startPosition"));
        assertEquals(1, listMusicChildren.size());

        assertTrue(listMusicChildren.get(0).getDuration() > 0.0);
    }

    @Test
    public void testM4bAudioTest() {
        LOG.info("start testM4bAudioTest");

        Path m4bAudioFile = MusicFolderTestData.resolveM4bAudioPath();
        MusicFolder musicFolder = new MusicFolder(m4bAudioFile, "m4b", Type.MEDIA, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        List<MediaFile> listMusicChildren = mediaFileRepository.findByFolderAndParentPath(musicFolder, "",
                Sort.by("startPosition"));
        assertEquals(3, listMusicChildren.size());
        MediaFile base = listMusicChildren.get(0);
        assertEquals(-1.0d, base.getStartPosition(), 0.01);
        assertEquals("m4btestbook", base.getTitle());
        assertEquals("m4btestartist", base.getArtist());
        assertEquals("m4btestartist", base.getAlbumArtist());
        assertEquals("m4btest", base.getAlbumName());

        MediaFile chapter1 = listMusicChildren.get(1);
        assertEquals(0.0d, chapter1.getStartPosition(), 0.01);
        assertEquals(2.665d, chapter1.getDuration(), 0.01);
        assertEquals(" Chapter 001  - 00:00:02", chapter1.getTitle());
        assertEquals("m4btest", chapter1.getAlbumName());

        MediaFile chapter2 = listMusicChildren.get(2);
        assertEquals(2.665d, chapter2.getStartPosition(), 0.01);
        assertEquals(3.715d, chapter2.getDuration(), 0.01);
        assertEquals(" Chapter 002  - 00:00:03", chapter2.getTitle());
        assertEquals("m4btest", chapter2.getAlbumName());

    }

    @Test
    public void testMusicCueAndFlac() {
        LOG.info("start testMusicCueAndFlac");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicCueAndFlacFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "CueAndFlac", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        Artist artist = allArtists.get(0);
        assertEquals("TestCueArtist", artist.getName());
        assertEquals(1, artist.getAlbumCount());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("AirsonicTest", album.getName());
        assertEquals("TestCueArtist", album.getArtist());
        assertEquals(2, album.getSongCount());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(3, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("flac", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.flac").toString(), file.getPath());
        assertTrue(file.getIndexPath().contains("airsonic-test.cue"));
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

        MediaFile track1 = albumFiles.get(1);
        assertEquals("Handel", track1.getTitle());
        assertEquals("flac", track1.getFormat());
        assertEquals(track1.getAlbumName(), "AirsonicTest");
        assertEquals("Beecham", track1.getArtist());
        assertEquals("TestCueArtist", track1.getAlbumArtist());
        assertEquals(1L, (long)track1.getTrackNumber());
        assertNull(track1.getYear());
        assertEquals(album.getPath(), track1.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.flac").toString(), track1.getPath());
        assertNull(track1.getIndexPath());
        assertEquals(0.0d, track1.getStartPosition(), 0.0d);
    }

    @Test
    public void testMusicFlacWithCue() {
        LOG.info("start testMusicFlacWithCue");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicFlacWithCueFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "FlacWithCue", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        Artist artist = allArtists.get(0);
        assertEquals("TestCueArtist", artist.getName());
        assertEquals(1, artist.getAlbumCount());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("AirsonicTest", album.getName());
        assertEquals("TestCueArtist", album.getArtist());
        assertEquals(2, album.getSongCount());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(3, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("flac", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.flac").toString(), file.getPath());
        assertTrue(file.getIndexPath().contains("airsonic-test.flac"));
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

        MediaFile track1 = albumFiles.get(1);
        assertEquals("Handel", track1.getTitle());
        assertEquals("flac", track1.getFormat());
        assertEquals(track1.getAlbumName(), "AirsonicTest");
        assertEquals("Beecham", track1.getArtist());
        assertEquals("TestCueArtist", track1.getAlbumArtist());
        assertEquals(1L, (long)track1.getTrackNumber());
        assertNull(track1.getYear());
        assertEquals(album.getPath(), track1.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.flac").toString(), track1.getPath());
        assertNull(track1.getIndexPath());
        assertEquals(0.0d, track1.getStartPosition(), 0.0d);
    }

}
