package org.airsonic.player.service.podcast;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.repository.PodcastChannelRepository;
import org.airsonic.player.repository.PodcastEpisodeRepository;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.VersionService;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {PodcastTestConfig.class})
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class PodcastDownloadClientIntegrationTest {

    @MockitoBean
    private VersionService versionService;

    @MockitoBean
    private CloseableHttpClient httpClient;

    @TempDir
    private Path tempFolder;

    @TempDir
    private static Path airsonicFolder;

    @Autowired
    private PodcastDownloadClient podcastDownloadClient;

    @Autowired
    private PodcastEpisodeRepository podcastEpisodeRepository;

    @Autowired
    private PodcastChannelRepository podcastChannelRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MediaFolderService mediaFolderService;

    private PodcastEpisode podcastEpisode;

    private MediaFile channelMediaFile;

    private String folderPath;

    @BeforeAll
    public static void init() {
        System.setProperty("airsonic.home", airsonicFolder.toString());
    }

    @BeforeEach
    public void setUp() throws IOException {
        MusicFolder podcastFolder = musicFolderRepository.findAll().stream()
                .filter(folder -> folder.getType() == MusicFolder.Type.PODCAST).findFirst().orElse(null);
        folderPath = podcastFolder.getPath().toString();
        podcastFolder.setPath(tempFolder);
        musicFolderRepository.saveAndFlush(podcastFolder);

        channelMediaFile = new MediaFile();
        channelMediaFile.setFolder(podcastFolder);
        channelMediaFile.setPresent(true);
        channelMediaFile.setChanged(Instant.now());
        channelMediaFile.setCreated(Instant.now());
        channelMediaFile.setParentPath("");
        channelMediaFile.setPath("test");
        channelMediaFile.setLastScanned(Instant.now());
        channelMediaFile.setChildrenLastUpdated(Instant.now());
        channelMediaFile.setMediaType(MediaFile.MediaType.PODCAST);
        mediaFileRepository.saveAndFlush(channelMediaFile);
        Files.createDirectories(tempFolder.resolve("test"));

        PodcastChannel podcastChannel = new PodcastChannel();
        podcastChannel.setUrl("http://example.com");
        podcastChannel.setMediaFile(channelMediaFile);
        podcastChannel.setTitle("Test");
        podcastChannel.setStatus(PodcastStatus.COMPLETED);
        podcastChannelRepository.save(podcastChannel);

        podcastEpisode = new PodcastEpisode();
        podcastEpisode.setUrl("http://example.com");
        podcastEpisode.setTitle("Test");
        podcastEpisode.setStatus(PodcastStatus.SKIPPED);
        podcastEpisode.setChannel(podcastChannel);
        podcastEpisodeRepository.save(podcastEpisode);
        mediaFolderService.clearMusicFolderCache();
    }

    @AfterEach
    public void tearDown() throws IOException {
        musicFolderRepository.findAll().stream().filter(folder -> folder.getType() == MusicFolder.Type.PODCAST)
                .findFirst().ifPresent(folder -> {
                    folder.setPath(Path.of(folderPath));
                    musicFolderRepository.saveAndFlush(folder);
                });
        // Clean up any MediaFile child rows the download created under the channel path —
        // testDownload writes "test/Test.mp3" as a child of channelMediaFile; without this,
        // a PODCAST media_file row with present=false is left behind, breaking the pre-state
        // assertion in IndexManagerTestCase.testExpunge:177 under matrix-DB ordering.
        mediaFileRepository.findByFolderAndParentPath(channelMediaFile.getFolder(), "test", Sort.unsorted())
                .forEach(mediaFileRepository::delete);
        mediaFileRepository.delete(channelMediaFile);
        podcastEpisodeRepository.delete(podcastEpisode);
        deleteDirectory(tempFolder.resolve("test"));
    }

    @Test
    public void testDownload() throws IOException {
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class))).thenAnswer(invocation -> {
            BasicClassicHttpResponse response = new BasicClassicHttpResponse(200, "test");
            response.setEntity(new ByteArrayEntity("non mp3 data".getBytes(), ContentType.APPLICATION_OCTET_STREAM));
            HttpClientResponseHandler<?> handler = invocation.getArgument(1);
            return handler.handleResponse(response);
        });
        podcastDownloadClient.downloadEpisode(podcastEpisode.getId()).join();
        PodcastEpisode episode = podcastEpisodeRepository.findById(podcastEpisode.getId()).orElse(null);
        assertNotNull(episode);
        assertEquals(episode.getStatus(), PodcastStatus.ERROR);
        List<MediaFile> mediaFiles = mediaFileRepository.findByFolderAndPath(channelMediaFile.getFolder(), "test");
        assertEquals(1, mediaFiles.size());
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.list(directory).forEach(file -> {
                try {
                    if (Files.isDirectory(file)) {
                        deleteDirectory(file);
                    } else {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Files.deleteIfExists(directory);
    }

}
