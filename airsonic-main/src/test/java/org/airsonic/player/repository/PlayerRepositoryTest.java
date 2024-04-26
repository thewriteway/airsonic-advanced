package org.airsonic.player.repository;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayerTechnology;
import org.airsonic.player.domain.TranscodeScheme;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of {@link PlayerDao}.
 *
 * @author Sindre Mehus
 */
@SpringBootTest
@EnableConfigurationProperties({ AirsonicHomeConfig.class })
@Transactional
public class PlayerRepositoryTest {

    @Autowired
    PlayerRepository playerRepository;

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
        jdbcTemplate.execute("delete from player");
    }

    @Test
    public void testCreatePlayer() {
        Player player = new Player();
        player.setName("name");
        player.setType("type");
        player.setUsername("username");
        player.setIpAddress("ipaddress");
        player.setDynamicIp(false);
        player.setAutoControlEnabled(false);
        player.setTechnology(PlayerTechnology.EXTERNAL_WITH_PLAYLIST);
        player.setClientId("android");
        player.setLastSeen(Instant.now().truncatedTo(ChronoUnit.MICROS));
        player.setTranscodeScheme(TranscodeScheme.MAX_160);

        playerRepository.save(player);
        Player newPlayer = playerRepository.findAll().get(0);
        assertPlayerEquals(player, newPlayer);

        Player newPlayer2 = playerRepository.findById(newPlayer.getId()).get();
        assertPlayerEquals(player, newPlayer2);
    }

    @Test
    public void testDefaultValues() {
        playerRepository.save(new Player());
        Player player = playerRepository.findAll().get(0);

        assertTrue(player.getDynamicIp());
        assertTrue(player.getAutoControlEnabled());
        assertNull(player.getClientId());
    }

    @Test
    public void testIdentity() {
        Player player = new Player();

        playerRepository.save(player);
        Integer playerId1 = player.getId();
        assertEquals(1, playerRepository.count());

        player = new Player();
        playerRepository.save(player);
        Integer playerId2 = player.getId();
        assertNotEquals(playerId1, playerId2);
        assertEquals(2, playerRepository.count());

        playerRepository.deleteById(playerId1);
        player = new Player();
        playerRepository.save(player);
        assertNotEquals(playerId1, player.getId());
        assertEquals(2, playerRepository.count());
    }


    @Test
    public void testGetPlayersForUserAndClientId() {
        Player player = new Player();
        player.setUsername("sindre");
        playerRepository.save(player);
        player = playerRepository.findAll().get(0);

        List<Player> players = playerRepository.findByUsernameAndClientIdIsNull("sindre");
        assertFalse(players.isEmpty());
        assertPlayerEquals(player, players.get(0));
        assertTrue(playerRepository.findByUsernameAndClientId("sindre", "foo").isEmpty());

        player.setClientId("foo");
        playerRepository.save(player);

        players = playerRepository.findByUsernameAndClientIdIsNull("sindre");
        assertTrue(players.isEmpty());
        players = playerRepository.findByUsernameAndClientId("sindre", "foo");
        assertFalse(players.isEmpty());
        assertPlayerEquals(player, players.get(0));
    }

    @Test
    public void testUpdatePlayer() {
        Player player = new Player();
        playerRepository.save(player);
        assertPlayerEquals(player, playerRepository.findAll().get(0));

        player.setName("name");
        player.setType("Winamp");
        player.setTechnology(PlayerTechnology.WEB);
        player.setClientId("foo");
        player.setUsername("username");
        player.setIpAddress("ipaddress");
        player.setDynamicIp(true);
        player.setAutoControlEnabled(false);
        player.setLastSeen(Instant.now().truncatedTo(ChronoUnit.MICROS));
        player.setTranscodeScheme(TranscodeScheme.MAX_160);

        playerRepository.save(player);
        Player newPlayer = playerRepository.findAll().get(0);
        assertPlayerEquals(player, newPlayer);
    }

    @Test
    public void testDeletePlayer() {
        assertEquals(0, playerRepository.count());

        Player p1 = new Player();
        playerRepository.save(p1);
        assertEquals(1, playerRepository.count());

        Player p2 = new Player();
        playerRepository.save(p2);
        assertEquals(2, playerRepository.count());

        playerRepository.deleteById(p1.getId());
        assertEquals(1, playerRepository.count());

        playerRepository.deleteById(p2.getId());
        assertEquals(0, playerRepository.count());
    }

    private void assertPlayerEquals(Player expected, Player actual) {
        assertThat(expected).usingRecursiveComparison().isEqualTo(actual);
    }
}
