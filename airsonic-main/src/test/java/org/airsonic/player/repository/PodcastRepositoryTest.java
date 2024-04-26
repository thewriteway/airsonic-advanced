package org.airsonic.player.repository;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of {@link PodcastDao}.
 *
 * @author Sindre Mehus
 */
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
@Transactional
public class PodcastRepositoryTest {

    @Autowired
    private PodcastChannelRepository podcastChannelRepository;

    @Autowired
    private PodcastEpisodeRepository podcastEpisodeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void beforeEach() {
        jdbcTemplate.execute("delete from podcast_channel");
    }

    @Test
    public void testCreateChannel() {
        PodcastChannel channel = new PodcastChannel("http://foo");
        podcastChannelRepository.save(channel);

        PodcastChannel newChannel = podcastChannelRepository.findAll().get(0);
        assertNotNull(newChannel.getId());
        assertChannelEquals(channel, newChannel);
    }

    @Test
    public void testChannelId() {
        PodcastChannel channel = podcastChannelRepository.save(new PodcastChannel("http://foo"));
        Integer channelId = channel.getId();

        assertEquals(channelId + 1, podcastChannelRepository.save(new PodcastChannel("http://foo")).getId());
        assertEquals(channelId + 2, podcastChannelRepository.save(new PodcastChannel("http://foo")).getId());
        assertEquals(channelId + 3, podcastChannelRepository.save(new PodcastChannel("http://foo")).getId());

        podcastChannelRepository.deleteById(channelId + 1);
        assertEquals(channelId + 4, podcastChannelRepository.save(new PodcastChannel("http://foo")).getId());

        podcastChannelRepository.deleteById(channelId + 4);
        assertEquals(channelId + 5, podcastChannelRepository.save(new PodcastChannel("http://foo")).getId());
    }

    @Test
    public void testUpdateChannel() {
        PodcastChannel channel = new PodcastChannel("http://foo");
        podcastChannelRepository.saveAndFlush(channel);
        channel = podcastChannelRepository.findAll().get(0);

        channel.setUrl("http://bar");
        channel.setTitle("Title");
        channel.setDescription("Description");
        channel.setImageUrl("http://foo/bar.jpg");
        channel.setStatus(PodcastStatus.ERROR);
        channel.setErrorMessage("Something went terribly wrong.");

        podcastChannelRepository.saveAndFlush(channel);
        PodcastChannel newChannel = podcastChannelRepository.findAll().get(0);

        assertEquals(channel.getId(), newChannel.getId());
        assertChannelEquals(channel, newChannel);
    }

    @Test
    public void testDeleteChannel() {
        long count = podcastChannelRepository.count();

        PodcastChannel channel = new PodcastChannel("http://foo");
        podcastChannelRepository.save(channel);
        assertEquals(count + 1, podcastChannelRepository.count());

        PodcastChannel channel2 = new PodcastChannel("http://foo");
        podcastChannelRepository.save(channel2);
        assertEquals(count + 2, podcastChannelRepository.count());

        podcastChannelRepository.delete(podcastChannelRepository.findAll().get(0));
        assertEquals(count + 1, podcastChannelRepository.count());

        podcastChannelRepository.delete(podcastChannelRepository.findAll().get(0));
        assertEquals(count, podcastChannelRepository.count());
    }

    @Test
    public void testCreateEpisode() {
        PodcastChannel channel = createChannel();
        PodcastEpisode episode = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "http://bar", null, "title", "description",
                Instant.now().truncatedTo(ChronoUnit.MICROS), "12:34", null, null, PodcastStatus.NEW, null);
        podcastEpisodeRepository.saveAndFlush(episode);

        List<PodcastEpisode> episodes = podcastEpisodeRepository.findByChannel(channel);

        PodcastEpisode newEpisode = episodes.get(0);
        assertNotNull(newEpisode.getId());
        assertEpisodeEquals(episode, newEpisode);
    }

    @Test
    public void testGetEpisode() {
        assertTrue(podcastEpisodeRepository.findById(23).isEmpty());

        PodcastChannel channel = createChannel();
        PodcastEpisode episode = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "http://bar", null, "title", "description",
                Instant.now().truncatedTo(ChronoUnit.MICROS), "12:34", 3276213L, 2341234L, PodcastStatus.NEW, "error");
        podcastEpisodeRepository.saveAndFlush(episode);

        List<PodcastEpisode> episodes = podcastEpisodeRepository.findByChannel(channel);
        int episodeId = episodes.get(0).getId();
        PodcastEpisode newEpisode = podcastEpisodeRepository.findById(episodeId).get();
        assertEpisodeEquals(episode, newEpisode);
    }

    @Test
    public void testGetEpisodes() {
        PodcastChannel channel = createChannel();
        PodcastEpisode a = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "a", null, null, null,
                Instant.ofEpochMilli(3000), null, null, null, PodcastStatus.NEW, null);
        PodcastEpisode b = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "b", null, null, null,
                Instant.ofEpochMilli(1000), null, null, null, PodcastStatus.NEW, "error");
        PodcastEpisode c = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "c", null, null, null,
                Instant.ofEpochMilli(2000), null, null, null, PodcastStatus.NEW, null);
        PodcastEpisode d = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "c", null, null, null,
                null, null, null, null, PodcastStatus.NEW, "");
        podcastEpisodeRepository.save(a);
        podcastEpisodeRepository.save(b);
        podcastEpisodeRepository.save(c);
        podcastEpisodeRepository.save(d);

        channel = podcastChannelRepository.findById(channel.getId()).get();
        List<PodcastEpisode> episodes = podcastEpisodeRepository.findByChannel(channel);
        assertEquals(4, episodes.size());
        assertEpisodeEquals(a, episodes.get(0));
        assertEpisodeEquals(b, episodes.get(1));
        assertEpisodeEquals(c, episodes.get(2));
        assertEpisodeEquals(d, episodes.get(3));
    }


    @Test
    public void testUpdateEpisode() {
        PodcastChannel channel = createChannel();
        PodcastEpisode episode = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "http://bar", null, null, null,
                null, null, null, null, PodcastStatus.NEW, null);
        podcastEpisodeRepository.save(episode);
        episode = podcastEpisodeRepository.findByChannel(channel).get(0);

        episode.setUrl("http://bar");
        episode.setTitle("Title");
        episode.setDescription("Description");
        episode.setPublishDate(Instant.now().truncatedTo(ChronoUnit.MICROS));
        episode.setDuration("1:20");
        episode.setBytesTotal(87628374612L);
        episode.setBytesDownloaded(9086L);
        episode.setStatus(PodcastStatus.DOWNLOADING);
        episode.setErrorMessage("Some error");

        podcastEpisodeRepository.save(episode);
        PodcastEpisode newEpisode = podcastEpisodeRepository.findByChannel(channel).get(0);
        assertEquals(episode.getId(), newEpisode.getId());
        assertEpisodeEquals(episode, newEpisode);
    }

    @Test
    public void testDeleteEpisode() {
        PodcastChannel channel = createChannel();

        assertEquals(0, podcastEpisodeRepository.findByChannel(channel).size());

        PodcastEpisode episode = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "http://bar", null, null, null,
                null, null, null, null, PodcastStatus.NEW, null);

        podcastEpisodeRepository.save(episode);
        assertEquals(1, podcastEpisodeRepository.findByChannel(channel).size());


        PodcastEpisode episode2 = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "http://bar", null, null, null,
                null, null, null, null, PodcastStatus.NEW, null);
        podcastEpisodeRepository.save(episode2);
        assertEquals(2, podcastEpisodeRepository.findByChannel(channel).size());

        podcastEpisodeRepository.delete(podcastEpisodeRepository.findByChannel(channel).get(0));
        assertEquals(1, podcastEpisodeRepository.findByChannel(channel).size());

        podcastEpisodeRepository.delete(podcastEpisodeRepository.findByChannel(channel).get(0));
        assertEquals(0, podcastEpisodeRepository.findByChannel(channel).size());
    }


    @Test
    public void testCascadingDelete() {
        PodcastChannel channel = createChannel();
        PodcastEpisode episode = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "http://bar", null, null, null,
                null, null, null, null, PodcastStatus.NEW, null);
        podcastEpisodeRepository.saveAndFlush(episode);
        PodcastEpisode episode2 = new PodcastEpisode(null, channel, UUID.randomUUID().toString(), "http://bar", null, null, null,
            null, null, null, null, PodcastStatus.NEW, null);
        podcastEpisodeRepository.saveAndFlush(episode2);
        channel = podcastChannelRepository.findById(channel.getId()).get();
        assertEquals(2, podcastEpisodeRepository.findByChannel(channel).size());

        podcastChannelRepository.delete(channel);
        assertEquals(0, podcastEpisodeRepository.findByChannel(channel).size());
    }

    private PodcastChannel createChannel() {
        PodcastChannel channel = new PodcastChannel("http://foo");
        podcastChannelRepository.save(channel);
        return channel;
    }

    private void assertChannelEquals(PodcastChannel expected, PodcastChannel actual) {
        assertEquals(expected.getUrl(), actual.getUrl());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getImageUrl(), actual.getImageUrl());
        assertSame(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getErrorMessage(), actual.getErrorMessage());
    }

    private void assertEpisodeEquals(PodcastEpisode expected, PodcastEpisode actual) {
        assertEquals(expected.getUrl(), actual.getUrl());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getPublishDate(), actual.getPublishDate());
        assertEquals(expected.getDuration(), actual.getDuration());
        assertEquals(expected.getBytesTotal(), actual.getBytesTotal());
        assertEquals(expected.getBytesDownloaded(), actual.getBytesDownloaded());
        assertSame(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getErrorMessage(), actual.getErrorMessage());
    }

}
