package org.airsonic.player.service;

import org.airsonic.player.domain.*;
import org.airsonic.player.repository.*;
import org.airsonic.player.service.PlaylistService.BroadcastedPlaylist;
import org.airsonic.player.service.cache.PlaylistCache;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;



@ExtendWith(MockitoExtension.class)
public class PlaylistServiceTest {

    @InjectMocks
    private PlaylistService playlistService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private AsyncWebSocketClient asyncWebSocketClient;

    @Mock
    private PlaylistCache playlistCache;

    private Playlist playlist;
    private User user;
    private User sharedUser;

    @BeforeEach
    private void setUp() {
        playlist = new Playlist();
        playlist.setId(1);
        playlist.setName("Test Playlist");
        playlist.setUsername("testuser");

        user = new User();
        user.setUsername("testuser");

        sharedUser = new User();
        sharedUser.setUsername("shareduser");

        List<User> sharedUsers = new ArrayList<>();
        sharedUsers.add(sharedUser);
        playlist.setSharedUsers(sharedUsers);

    }

    @Test
    public void testGetAllPlaylists() {
        List<Playlist> playlists = List.of(playlist);
        when(playlistRepository.findAll(any(Sort.class))).thenReturn(playlists);

        List<Playlist> result = playlistService.getAllPlaylists();

        assertThat(result).isEqualTo(playlists);
        verify(playlistRepository).findAll(any(Sort.class));
    }

    @Test
    public void testGetReadablePlaylistsForUser() {
        List<Playlist> playlists = List.of(playlist);
        when(playlistRepository.findByUsername(anyString())).thenReturn(playlists);
        when(playlistRepository.findBySharedTrue()).thenReturn(playlists);
        when(playlistRepository.findByUsernameNotAndSharedUsersUsername(anyString(), anyString())).thenReturn(playlists);

        List<Playlist> result = playlistService.getReadablePlaylistsForUser("testuser");

        assertThat(result).hasSize(1);
        verify(playlistRepository).findByUsername(anyString());
        verify(playlistRepository).findBySharedTrue();
        verify(playlistRepository).findByUsernameNotAndSharedUsersUsername(anyString(), anyString());
    }

    @Test
    public void testGetWritablePlaylistsForUser() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));
        when(playlistRepository.findByUsernameOrderByNameAsc(anyString())).thenReturn(List.of(playlist));

        List<Playlist> result = playlistService.getWritablePlaylistsForUser("testuser");

        assertThat(result).hasSize(1);
        verify(userRepository).findByUsername(anyString());
        verify(playlistRepository).findByUsernameOrderByNameAsc(anyString());
    }

    @Test
    public void testGetPlaylist() {
        when(playlistCache.getPlaylistById(anyInt())).thenReturn(null);
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));

        Playlist result = playlistService.getPlaylist(1);

        assertThat(result).isEqualTo(playlist);
        verify(playlistCache).getPlaylistById(anyInt());
        verify(playlistRepository).findById(anyInt());
        verify(playlistCache).putPlaylistById(anyInt(), any(Playlist.class));
    }

    @Test
    public void testGetPlaylistUsers() {
        List<User> users = List.of(user);
        playlist.setSharedUsers(users);
        when(playlistCache.getUsersForPlaylist(anyInt())).thenReturn(Collections.emptyList());
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));

        List<String> result = playlistService.getPlaylistUsers(1);

        assertThat(result).contains("testuser");
        verify(playlistCache).getUsersForPlaylist(anyInt());
        verify(playlistRepository).findById(anyInt());
        verify(playlistCache).putUsersForPlaylist(anyInt(), anyList());
    }

    @Test
    public void testGetFilesInPlaylist() {
        // Create 10 media files, half of them are present in the playlist
        List<PlaylistMediaFile> playlistMediaFiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MediaFile mediaFile = new MediaFile();
            mediaFile.setId(i);
            mediaFile.setPresent(i % 2 == 0);
            playlistMediaFiles.add(new PlaylistMediaFile(playlist, mediaFile, i));
        }
        playlist.setPlaylistMediaFiles(playlistMediaFiles);
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));

        // Get files in the playlist
        List<MediaFile> result = playlistService.getFilesInPlaylist(1);

        // Assert that only the present files are returned
        assertThat(result).hasSize(5);
        for (int i = 0; i < 5; i++) {
            assertThat(result.get(i).getId()).isEqualTo(i * 2);
        }
        // Verify that the playlist was fetched and the files were filtered
        verify(playlistRepository).findById(anyInt());
    }

    @Test
    public void testSetFilesInPlaylist() {
        List<MediaFile> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MediaFile mediaFile = new MediaFile();
            mediaFile.setId(i);
            mediaFile.setPresent(true);
            mediaFile.setDuration(1000.0 * i);
            files.add(mediaFile);
        }
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));
        when(playlistRepository.saveAndFlush(any(Playlist.class))).thenReturn(playlist);

        Playlist result = playlistService.setFilesInPlaylist(1, files);

        assertThat(result).isEqualTo(playlist);
        verify(playlistRepository).findById(anyInt());
        verify(playlistRepository).saveAndFlush(any(Playlist.class));
        assertThat(result.getDuration()).isEqualTo(1000.0 * 10);
        assertThat(result.getFileCount()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            assertThat(result.getPlaylistMediaFiles().get(i).getOrderIndex()).isEqualTo(i);
            assertThat(result.getPlaylistMediaFiles().get(i).getMediaFile().getId()).isEqualTo(i);
        }
    }

    @Test
    public void testRemoveFilesInPlaylistByIndices() {
        List<PlaylistMediaFile> playlistMediaFiles = new ArrayList<>();
        playlistMediaFiles.add(new PlaylistMediaFile(playlist, new MediaFile(), 0));
        playlist.setPlaylistMediaFiles(playlistMediaFiles);
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));
        assertEquals(1, playlist.getPlaylistMediaFiles().size());

        playlistService.removeFilesInPlaylistByIndices(1, List.of(0));

        verify(playlistCache).removePlaylistById(anyInt());
        verify(playlistRepository).findById(anyInt());
        verify(playlistRepository).save(any(Playlist.class));
        assertEquals(0, playlist.getPlaylistMediaFiles().size());
    }

    @Test
    public void testRefreshPlaylistsStats() {
        List<Playlist> playlists = List.of(playlist);
        when(playlistRepository.findAll()).thenReturn(playlists);

        List<Playlist> result = playlistService.refreshPlaylistsStats();

        assertThat(result).hasSize(1);
        verify(playlistRepository).findAll();
        verify(playlistRepository).save(any(Playlist.class));
    }

    @Test
    public void testCreatePlaylist() {
        when(playlistRepository.save(any(Playlist.class))).thenReturn(playlist);

        Playlist result = playlistService.createPlaylist("Test Playlist", true, "testuser");

        verify(playlistRepository).save(any(Playlist.class));
        assertEquals("Test Playlist", result.getName());
        assertEquals(true, result.getShared());
        assertEquals("testuser", result.getUsername());
        assertEquals(result.getChanged(), result.getChanged());
    }

    @Test
    public void testAddPlaylistUser() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));

        playlistService.addPlaylistUser(playlist, "testuser");

        verify(playlistCache).removeUsersForPlaylist(anyInt());
        verify(userRepository).findByUsername(anyString());
        verify(playlistRepository).findById(anyInt());
        verify(playlistRepository).save(any(Playlist.class));
        verify(asyncWebSocketClient).sendToUser(anyString(), anyString(), any());
    }

    @Test
    public void testAddPlaylistUserWhenUserAlreadyAdded() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(sharedUser));
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));

        playlistService.addPlaylistUser(playlist, "shareduser");

        verify(playlistCache).removeUsersForPlaylist(anyInt());
        verify(userRepository).findByUsername(anyString());
        verify(playlistRepository).findById(anyInt());
        verify(playlistRepository, never()).save(any(Playlist.class));
        verify(asyncWebSocketClient, never()).sendToUser(anyString(), anyString(), any());
    }



    @Test
    public void testDeletePlaylistUser() {
        when(playlistRepository.findByIdAndSharedUsersUsername(anyInt(), anyString())).thenReturn(Optional.of(playlist));

        playlistService.deletePlaylistUser(playlist, "testuser");

        verify(playlistCache).removeUsersForPlaylist(anyInt());
        verify(playlistRepository).findByIdAndSharedUsersUsername(anyInt(), anyString());
        verify(playlistRepository).save(any(Playlist.class));
        verify(asyncWebSocketClient).sendToUser(anyString(), anyString(), any());
    }

    @ParameterizedTest
    @CsvSource({
        ", true, false",
        "testuser, false, true",
        "nonSharedUser, true, true" })
    public void testIsReadAllowedWithoutCacheAccess(String username, boolean isShared, boolean expected) {
        playlist.setShared(isShared);

        boolean result = playlistService.isReadAllowed(playlist, username);

        assertEquals(expected, result);
    }

    @ParameterizedTest
    @CsvSource({
        "shareduser, true",
        "nonSharedUser, false"
    })
    public void testIsReadAllowedWithEmptyCache(String username, boolean expected) {
        when(playlistCache.getUsersForPlaylist(anyInt())).thenReturn(Collections.emptyList());
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));

        boolean result = playlistService.isReadAllowed(playlist, username);
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @CsvSource({
        "shareduser, true",
        "nonSharedUser, false"
    })
    public void testIsReadAllowedWithCache(String username, boolean expected) {
        when(playlistCache.getUsersForPlaylist(anyInt())).thenReturn(List.of("shareduser"));
        boolean result = playlistService.isReadAllowed(playlist, username);
        assertEquals(expected, result);
        verifyNoInteractions(playlistRepository);
    }

    @Test
    public void testIsExist() {
        when(playlistRepository.existsById(anyInt())).thenReturn(true);

        boolean result = playlistService.isExist(1);

        assertThat(result).isTrue();
        verify(playlistRepository).existsById(anyInt());
    }

    @Test
    public void testIsWriteAllowed() {
        when(playlistRepository.existsByIdAndUsername(anyInt(), anyString())).thenReturn(true);

        boolean result = playlistService.isWriteAllowed(1, "testuser");

        assertThat(result).isTrue();
        verify(playlistRepository).existsByIdAndUsername(anyInt(), anyString());
    }

    @Test
    public void testDeletePlaylist() {
        doNothing().when(playlistRepository).deleteById(anyInt());

        playlistService.deletePlaylist(1);

        verify(playlistCache).removePlaylistById(anyInt());
        verify(playlistCache).removeUsersForPlaylist(anyInt());
        verify(playlistRepository).deleteById(anyInt());
        verify(asyncWebSocketClient).send(anyString(), anyInt());
    }

    @Test
    public void testBroadcastDeleted() {
        playlistService.broadcastDeleted(1);

        verify(asyncWebSocketClient).send(anyString(), anyInt());
    }

    @Test
    public void testUpdatePlaylist() {
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));

        playlistService.updatePlaylist(1, "Updated Playlist");

        verify(playlistCache).removePlaylistById(anyInt());
        verify(playlistRepository).findById(anyInt());
        verify(playlistRepository).save(any(Playlist.class));
    }

    @Test
    public void testBroadcastWithSharedPlaylist() {
        playlist.setShared(true);

        playlistService.broadcast(playlist);

        verify(asyncWebSocketClient).send(eq("/topic/playlists/updated"), eq(playlist));
        verifyNoMoreInteractions(asyncWebSocketClient);
    }

    @Test
    public void testBroadcastWithNonSharedPlaylist() {
        playlist.setShared(false);

        playlistService.broadcast(playlist);

        verify(asyncWebSocketClient).sendToUser(eq("testuser"), eq("/queue/playlists/updated"), eq(playlist));
        verifyNoMoreInteractions(asyncWebSocketClient);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testBroadcastFileChangeWithSharedPlaylist(boolean isShared) {
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));
        playlist.setShared(true);

        playlistService.broadcastFileChange(1, isShared, true);

        verify(asyncWebSocketClient).send(eq("/topic/playlists/updated"), any(BroadcastedPlaylist.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testBroadcastFileChangeWithNonSharedPlaylist(boolean isShared) {
        // Given
        playlist.setShared(false);
        when(playlistRepository.findById(anyInt())).thenReturn(Optional.of(playlist));

        // When
        playlistService.broadcastFileChange(1, isShared, true);

        // Then
        if (isShared) {
            verify(asyncWebSocketClient).send(eq("/topic/playlists/deleted"), eq(1));
        }
        verify(asyncWebSocketClient).sendToUser(eq("testuser"), eq("/queue/playlists/updated"), any(BroadcastedPlaylist.class));
        verify(asyncWebSocketClient).sendToUser(eq("shareduser"), eq("/queue/playlists/updated"), any(BroadcastedPlaylist.class));
        verifyNoMoreInteractions(asyncWebSocketClient);
    }

}