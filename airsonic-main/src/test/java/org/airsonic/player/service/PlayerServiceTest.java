package org.airsonic.player.service;

import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.repository.PlayerRepository;
import org.airsonic.player.repository.TranscodingRepository;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private TranscodingRepository transcodingRepository;

    @Mock
    private AsyncWebSocketClient asyncWebSocketClient;

    @InjectMocks
    private PlayerService playerService;

    @Test
    public void testPlaylist() {

        // given
        when(transcodingRepository.findByDefaultActiveTrue()).thenReturn(new ArrayList<>());
        Player player = new Player();
        player.setUsername("test");
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
            Player p = invocation.getArgument(0);
            p.setId(0);
            return p;
        }).thenAnswer(invocation -> {
            Player p = invocation.getArgument(0);
            p.setId(1);
            return p;
        });

        // when
        Player actual = playerService.createPlayer(player);

        // then
        verify(playerRepository, times(3)).save(any(Player.class));
        verify(playerRepository).delete(any(Player.class));
        verify(asyncWebSocketClient).sendToUser(eq("test"), eq("/queue/players/created"), any());
        PlayQueue playQueue = actual.getPlayQueue();
        assertNotNull(playQueue);
    }

}
