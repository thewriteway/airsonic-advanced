package org.airsonic.player.ajax;

import org.airsonic.player.ajax.PlaylistWSController.PlaylistFilesAddResult;
import org.airsonic.player.ajax.PlaylistWSController.PlaylistFilesModificationRequest;
import org.airsonic.player.ajax.PlaylistWSController.PlaylistUpdateRequest;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PlaylistService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistWSControllerTest {

    private static final String USER = "user";
    private static final Principal PRINCIPAL = () -> USER;
    private static final int ID = 7;

    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private PlaylistService playlistService;
    @Mock
    private SecurityService securityService;
    @Mock
    private PlayerService playerService;
    @Mock
    private LocaleResolver localeResolver;
    @InjectMocks
    private PlaylistWSController controller;

    private Playlist playlist;

    @BeforeEach
    void setUp() {
        playlist = new Playlist();
        playlist.setId(ID);
        playlist.setName("Road trip");
        playlist.setUsername(USER);
        lenient().when(playlistService.getPlaylist(ID)).thenReturn(playlist);
    }

    private void owner() {
        when(playlistService.isWriteAllowed(same(playlist), eq(USER))).thenReturn(true);
    }

    private void notOwner(boolean admin) {
        playlist.setUsername("someone-else");
        when(playlistService.isWriteAllowed(same(playlist), eq(USER))).thenReturn(false);
        when(securityService.isAdmin(USER)).thenReturn(admin);
    }

    private static MediaFile file(int id, MediaType type) {
        MediaFile file = new MediaFile();
        file.setId(id);
        file.setMediaType(type);
        return file;
    }

    private static PlaylistFilesModificationRequest request(int playlistId, Integer... ids) {
        PlaylistFilesModificationRequest req = new PlaylistFilesModificationRequest();
        req.setId(playlistId);
        req.setModifierIds(new ArrayList<>(List.of(ids)));
        return req;
    }

    private static PlaylistUpdateRequest update(int playlistId) {
        PlaylistUpdateRequest req = new PlaylistUpdateRequest();
        req.setId(playlistId);
        req.setName("Renamed");
        req.setComment("");
        return req;
    }

    /** Every request that changes the playlist, by name. */
    private Map<String, Executable> changes(int playlistId) {
        Map<String, Executable> calls = new LinkedHashMap<>();
        calls.put("delete", () -> controller.deletePlaylist(PRINCIPAL, playlistId));
        calls.put("update", () -> controller.updatePlaylist(PRINCIPAL, update(playlistId)));
        calls.put("append", () -> controller.appendToPlaylist(PRINCIPAL, request(playlistId, 1)));
        calls.put("remove", () -> controller.remove(PRINCIPAL, request(playlistId, 0)));
        calls.put("moveup", () -> controller.up(PRINCIPAL, request(playlistId, 1)));
        calls.put("movedown", () -> controller.down(PRINCIPAL, request(playlistId, 0)));
        calls.put("rearrange", () -> controller.rearrange(PRINCIPAL, request(playlistId, 1, 0)));
        return calls;
    }

    private void verifyUnchanged() {
        verify(playlistService, never()).deletePlaylist(anyInt());
        verify(playlistService, never()).updatePlaylist(anyInt(), any(), any(), any());
        verify(playlistService, never()).setFilesInPlaylist(anyInt(), any());
        verify(playlistService, never()).removeFilesInPlaylistByIndices(any(), any());
        verify(playlistService, never()).broadcastFileChange(any(), anyBoolean(), anyBoolean());
        verify(playlistService, never()).broadcastDeleted(anyInt());
    }

    @Test
    void changesAreRefusedForPlaylistsOfOtherUsers() {
        notOwner(false);

        changes(ID).forEach((name, call) -> assertThrows(AccessDeniedException.class, call, name));

        verifyUnchanged();
    }

    @Test
    void changesAreRefusedForMissingPlaylists() {
        changes(99).forEach((name, call) -> assertThrows(AccessDeniedException.class, call, name));

        verifyUnchanged();
    }

    @Test
    void administratorsMayChangePlaylistsOfOtherUsers() {
        notOwner(true);

        controller.deletePlaylist(PRINCIPAL, ID);
        controller.updatePlaylist(PRINCIPAL, update(ID));

        verify(playlistService).deletePlaylist(ID);
        verify(playlistService).updatePlaylist(ID, "Renamed", "", false);
    }

    @Test
    void readsAreRefusedForPlaylistsTheUserMayNotSee() {
        when(playlistService.isReadAllowed(playlist, USER)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> controller.getPlaylist(PRINCIPAL, ID));
        assertThrows(AccessDeniedException.class, () -> controller.getPlaylistEntries(PRINCIPAL, ID));
        assertThrows(AccessDeniedException.class, () -> controller.getPlaylistEntries(PRINCIPAL, 99));
        verify(playlistService, never()).getFilesInPlaylist(anyInt(), anyBoolean());
    }

    @Test
    void readablePlaylistEntriesAreReturned() {
        when(playlistService.isReadAllowed(playlist, USER)).thenReturn(true);
        List<MediaFile> files = List.of(file(1, MediaType.MUSIC));
        when(playlistService.getFilesInPlaylist(ID, true)).thenReturn(files);
        when(mediaFileService.toMediaFileEntryList(files, USER, true, true, null, null, null)).thenReturn(List.of());

        assertEquals(List.of(), controller.getPlaylistEntries(PRINCIPAL, ID));
    }

    @Test
    void playQueueOfAnotherUsersPlayerCannotBeSaved() {
        Player player = new Player();
        player.setUsername("someone-else");
        when(playerService.getPlayerById(3)).thenReturn(player);

        assertThrows(AccessDeniedException.class, () -> controller.createPlaylistForPlayQueue(PRINCIPAL, 3));
        assertThrows(AccessDeniedException.class, () -> controller.createPlaylistForPlayQueue(PRINCIPAL, 4));
        verify(playlistService, never()).createPlaylistForPlayQueue(any(), anyString(), any());
    }

    @Test
    void appendSkipsFilesOutsideTheUsersFolders() {
        owner();
        MediaFile existing = file(1, MediaType.MUSIC);
        MediaFile allowed = file(2, MediaType.MUSIC);
        MediaFile forbidden = file(3, MediaType.MUSIC);
        when(playlistService.getFilesInPlaylist(ID, true)).thenReturn(List.of(existing));
        when(mediaFileService.getMediaFile(2)).thenReturn(allowed);
        when(mediaFileService.getMediaFile(3)).thenReturn(forbidden);
        when(securityService.isFolderAccessAllowed(same(allowed), eq(USER))).thenReturn(true);
        when(securityService.isFolderAccessAllowed(same(forbidden), eq(USER))).thenReturn(false);

        assertEquals(ID, controller.appendToPlaylist(PRINCIPAL, request(ID, 2, 3)));

        verify(playlistService).setFilesInPlaylist(ID, List.of(existing, allowed));
    }

    @Test
    void moveIgnoresIndicesOutsideThePlaylist() {
        owner();
        when(playlistService.getFilesInPlaylist(ID, true)).thenReturn(List.of(file(1, MediaType.MUSIC), file(2, MediaType.MUSIC)));

        controller.up(PRINCIPAL, request(ID, 5));
        controller.down(PRINCIPAL, request(ID, -1));
        controller.down(PRINCIPAL, request(ID, 1));

        verify(playlistService, never()).setFilesInPlaylist(anyInt(), any());
    }

    @Test
    void rearrangeAppliesOnlyACompleteNewOrder() {
        owner();
        MediaFile first = file(1, MediaType.MUSIC);
        MediaFile second = file(2, MediaType.MUSIC);
        when(playlistService.getFilesInPlaylist(ID, true)).thenReturn(List.of(first, second));

        controller.rearrange(PRINCIPAL, request(ID, 0));
        controller.rearrange(PRINCIPAL, request(ID, 0, 0));
        controller.rearrange(PRINCIPAL, request(ID, 0, 2));
        verify(playlistService, never()).setFilesInPlaylist(anyInt(), any());

        controller.rearrange(PRINCIPAL, request(ID, 1, 0));
        verify(playlistService).setFilesInPlaylist(ID, List.of(second, first));
    }

    @Test
    void addToPlaylistRefusesPlaylistsTheUserMayNotChange() {
        notOwner(false);

        PlaylistFilesAddResult result = controller.addToPlaylist(PRINCIPAL, request(ID, 1));

        assertEquals(new PlaylistFilesAddResult(ID, null, 0, false), result);
        verifyUnchanged();
    }

    @Test
    @SuppressWarnings("unchecked")
    void addToPlaylistAppendsSongsOfDirectoriesWithoutVideos() {
        owner();
        MediaFile existing = file(1, MediaType.MUSIC);
        MediaFile song = file(2, MediaType.MUSIC);
        MediaFile album = file(3, MediaType.ALBUM);
        MediaFile albumSong = file(4, MediaType.MUSIC);
        MediaFile albumVideo = file(5, MediaType.VIDEO);
        when(playlistService.getFilesInPlaylist(ID, true)).thenReturn(List.of(existing));
        when(mediaFileService.getMediaFile(2)).thenReturn(song);
        when(mediaFileService.getMediaFile(3)).thenReturn(album);
        when(mediaFileService.getDescendantsOf(album, true)).thenReturn(List.of(albumSong, albumVideo));
        doAnswer(invocation -> ((List<MediaFile>) invocation.getArgument(0)).removeIf(MediaFile::isVideo))
                .when(mediaFileService).removeVideoFiles(any());
        when(securityService.isFolderAccessAllowed(any(), any())).thenReturn(true);

        PlaylistFilesAddResult result = controller.addToPlaylist(PRINCIPAL, request(ID, 2, 3));

        assertEquals(new PlaylistFilesAddResult(ID, "Road trip", 2, true), result);
        verify(playlistService).setFilesInPlaylist(ID, List.of(existing, song, albumSong));
        verify(playlistService).broadcastFileChange(ID, false, true);
    }

    @Test
    void addToPlaylistSkipsFilesOutsideTheUsersFolders() {
        owner();
        MediaFile allowed = file(2, MediaType.MUSIC);
        MediaFile forbidden = file(3, MediaType.MUSIC);
        when(playlistService.getFilesInPlaylist(ID, true)).thenReturn(List.of());
        when(mediaFileService.getMediaFile(2)).thenReturn(allowed);
        when(mediaFileService.getMediaFile(3)).thenReturn(forbidden);
        when(securityService.isFolderAccessAllowed(same(allowed), eq(USER))).thenReturn(true);
        when(securityService.isFolderAccessAllowed(same(forbidden), eq(USER))).thenReturn(false);

        PlaylistFilesAddResult result = controller.addToPlaylist(PRINCIPAL, request(ID, 2, 3, 99));

        assertEquals(1, result.added());
        verify(playlistService).setFilesInPlaylist(ID, List.of(allowed));
    }

    @Test
    void addToPlaylistLeavesThePlaylistAloneWhenNothingIsAdded() {
        owner();
        lenient().when(mediaFileService.getMediaFile(anyInt())).thenReturn(null);

        PlaylistFilesAddResult result = controller.addToPlaylist(PRINCIPAL, request(ID, 99));

        assertEquals(new PlaylistFilesAddResult(ID, "Road trip", 0, true), result);
        verifyUnchanged();
    }
}
