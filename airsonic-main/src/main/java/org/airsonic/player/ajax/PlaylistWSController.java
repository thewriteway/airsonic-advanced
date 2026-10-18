package org.airsonic.player.ajax;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PlaylistService;
import org.airsonic.player.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Controller
@MessageMapping("/playlists")
public class PlaylistWSController {
    private static final Logger LOG = LoggerFactory.getLogger(PlaylistWSController.class);

    private final MediaFileService mediaFileService;
    private final PlaylistService playlistService;
    private final LocaleResolver localeResolver;
    private final SecurityService securityService;
    private final PlayerService playerService;

    public PlaylistWSController(MediaFileService mediaFileService, PlaylistService playlistService,
            LocaleResolver localeResolver, SecurityService securityService, PlayerService playerService) {
        this.mediaFileService = mediaFileService;
        this.playlistService = playlistService;
        this.localeResolver = localeResolver;
        this.securityService = securityService;
        this.playerService = playerService;
    }

    /**
     * Requests for playlists the user may not see or change get no reply. The pages only offer what
     * the user may do, so a refusal means a forged or outdated request.
     */
    @MessageExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException e) {
        LOG.warn("Refused playlist request: {}", e.getMessage());
    }

    /** The playlist, if the user may see it. */
    private Playlist readablePlaylist(int id, String username) {
        Playlist playlist = playlistService.getPlaylist(id);
        if (playlist == null || !playlistService.isReadAllowed(playlist, username)) {
            throw new AccessDeniedException("Playlist " + id + " is not readable by " + username);
        }
        return playlist;
    }

    /** The playlist, if the user may change it. */
    private Playlist editablePlaylist(int id, String username) {
        Playlist playlist = playlistService.getPlaylist(id);
        if (playlist == null || !isEditAllowed(playlist, username)) {
            throw new AccessDeniedException("Playlist " + id + " is not writable by " + username);
        }
        return playlist;
    }

    /** Owners and administrators may change a playlist, as on the playlist page (PlaylistController). */
    private boolean isEditAllowed(Playlist playlist, String username) {
        return playlistService.isWriteAllowed(playlist, username) || securityService.isAdmin(username);
    }

    /** The media files with the given ids that exist and are in the user's music folders. */
    private List<MediaFile> accessibleFiles(List<Integer> ids, String username) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .map(mediaFileService::getMediaFile)
                .filter(Objects::nonNull)
                .filter(file -> securityService.isFolderAccessAllowed(file, username))
                .toList();
    }

    @SubscribeMapping("/readable")
    public List<Playlist> getReadablePlaylists(Principal p) {
        return playlistService.getReadablePlaylistsForUser(p.getName());
    }

    @MessageMapping("/writable")
    @SendToUser(broadcast = false)
    public List<Playlist> getWritablePlaylists(Principal p) {
        return playlistService.getWritablePlaylistsForUser(p.getName());
    }

    @MessageMapping("/create/empty")
    @SendToUser(broadcast = false)
    public int createEmptyPlaylist(Principal p) {
        Locale locale = localeResolver.resolveLocale(p.getName());
        DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale);
        Instant now = Instant.now();
        Playlist result = playlistService.createPlaylist(dateFormat.format(now.atZone(ZoneId.systemDefault())), false, p.getName());
        playlistService.broadcast(result);
        return result.getId();
    }

    @MessageMapping("/create/starred")
    @SendToUser(broadcast = false)
    public int createPlaylistForStarredSongs(Principal p) {
        String username = p.getName();
        Locale locale = localeResolver.resolveLocale(username);
        Integer playlistId = playlistService.createPlaylistForStarredSongs(username, locale);
        playlistService.broadcastFileChange(playlistId, false, true);
        return playlistId;
    }

    @MessageMapping("/create/playqueue")
    @SendToUser(broadcast = false)
    public int createPlaylistForPlayQueue(Principal p, Integer playerId) throws Exception {
        String username = p.getName();
        Player player = playerService.getPlayerById(playerId);
        if (player == null || !username.equals(player.getUsername())) {
            throw new AccessDeniedException("Player " + playerId + " does not belong to " + username);
        }
        Locale locale = localeResolver.resolveLocale(username);
        Integer playlistId = playlistService.createPlaylistForPlayQueue(playerId, username, locale);
        playlistService.broadcastFileChange(playlistId, false, true);
        return playlistId;
    }

    @MessageMapping("/delete")
    public void deletePlaylist(Principal p, int id) {
        editablePlaylist(id, p.getName());
        playlistService.deletePlaylist(id);
        playlistService.broadcastDeleted(id);
    }

    @MessageMapping("/update")
    public void updatePlaylist(Principal p, PlaylistUpdateRequest req) {
        Playlist playlist = editablePlaylist(req.getId(), p.getName());
        playlistService.updatePlaylist(req.getId(), req.getName(), req.getComment(), req.getShared());
        playlistService.broadcastFileChange(req.getId(), playlist.getShared(), false);
    }

    @MessageMapping("/files/append")
    @SendToUser(broadcast = false)
    public int appendToPlaylist(Principal p, PlaylistFilesModificationRequest req) {
        // in this context, modifierIds are mediafile ids
        String username = p.getName();
        editablePlaylist(req.getId(), username);
        List<MediaFile> files = new ArrayList<>(playlistService.getFilesInPlaylist(req.getId(), true));
        files.addAll(accessibleFiles(req.getModifierIds(), username));

        playlistService.setFilesInPlaylist(req.getId(), files);
        playlistService.broadcastFileChange(req.getId(), false, true);

        return req.getId();
    }

    /**
     * Adds songs to the end of a playlist, e.g. when they are dropped on it. Unlike /files/append,
     * directories (albums, artists) add the songs they contain, and the playlist must be writable by
     * the user.
     */
    @MessageMapping("/files/add")
    @SendToUser(broadcast = false)
    public PlaylistFilesAddResult addToPlaylist(Principal p, PlaylistFilesModificationRequest req) {
        // in this context, modifierIds are mediafile ids
        String username = p.getName();
        Playlist playlist = playlistService.getPlaylist(req.getId());
        if (playlist == null || !isEditAllowed(playlist, username)) {
            return new PlaylistFilesAddResult(req.getId(), null, 0, false);
        }

        List<MediaFile> added = new ArrayList<>();
        for (MediaFile file : accessibleFiles(req.getModifierIds(), username)) {
            if (file.isDirectory()) {
                List<MediaFile> songs = new ArrayList<>(mediaFileService.getDescendantsOf(file, true));
                mediaFileService.removeVideoFiles(songs);
                added.addAll(songs);
            } else {
                added.add(file);
            }
        }
        if (!added.isEmpty()) {
            List<MediaFile> files = new ArrayList<>(playlistService.getFilesInPlaylist(playlist.getId(), true));
            files.addAll(added);
            playlistService.setFilesInPlaylist(playlist.getId(), files);
            playlistService.broadcastFileChange(playlist.getId(), false, true);
        }
        return new PlaylistFilesAddResult(playlist.getId(), playlist.getName(), added.size(), true);
    }

    @MessageMapping("/files/remove")
    @SendToUser(broadcast = false)
    public int remove(Principal p, PlaylistFilesModificationRequest req) {
        // in this context, modifierIds are indices
        editablePlaylist(req.getId(), p.getName());
        List<Integer> indices = new ArrayList<>(req.getModifierIds() == null ? List.of() : req.getModifierIds());
        indices.removeIf(Objects::isNull);
        Collections.sort(indices);
        playlistService.removeFilesInPlaylistByIndices(req.getId(), indices);
        playlistService.broadcastFileChange(req.getId(), false, true);
        return req.getId();
    }

    @MessageMapping("/files/moveup")
    @SendToUser(broadcast = false)
    public int up(Principal p, PlaylistFilesModificationRequest req) {
        // in this context, modifierIds has one element that is the index of the file
        editablePlaylist(req.getId(), p.getName());
        List<MediaFile> files = new ArrayList<>(playlistService.getFilesInPlaylist(req.getId(), true));
        Integer index = singleIndex(req);
        if (index != null && index > 0 && index < files.size()) {
            Collections.swap(files, index, index - 1);
            playlistService.setFilesInPlaylist(req.getId(), files);
            playlistService.broadcastFileChange(req.getId(), false, true);
        }

        return req.getId();
    }

    @MessageMapping("/files/movedown")
    @SendToUser(broadcast = false)
    public int down(Principal p, PlaylistFilesModificationRequest req) {
        // in this context, modifierIds has one element that is the index of the file
        editablePlaylist(req.getId(), p.getName());
        List<MediaFile> files = new ArrayList<>(playlistService.getFilesInPlaylist(req.getId(), true));
        Integer index = singleIndex(req);
        if (index != null && index >= 0 && index < files.size() - 1) {
            Collections.swap(files, index, index + 1);
            playlistService.setFilesInPlaylist(req.getId(), files);
            playlistService.broadcastFileChange(req.getId(), false, true);
        }

        return req.getId();
    }

    @MessageMapping("/files/rearrange")
    @SendToUser(broadcast = false)
    public int rearrange(Principal p, PlaylistFilesModificationRequest req) {
        // in this context, modifierIds are indices: the new order of all the files
        editablePlaylist(req.getId(), p.getName());
        List<MediaFile> files = playlistService.getFilesInPlaylist(req.getId(), true);
        List<Integer> order = req.getModifierIds();
        if (!isPermutation(order, files.size())) {
            // the requested order is not logged: it comes from the client
            LOG.warn("Ignored rearrangement of playlist {}: the new order does not list each of its {} files once", req.getId(), files.size());
            return req.getId();
        }
        playlistService.setFilesInPlaylist(req.getId(), order.stream().map(files::get).toList());
        playlistService.broadcastFileChange(req.getId(), false, true);

        return req.getId();
    }

    private static Integer singleIndex(PlaylistFilesModificationRequest req) {
        List<Integer> ids = req.getModifierIds();
        return ids != null && ids.size() == 1 ? ids.get(0) : null;
    }

    /** Whether the indices name each of 0..size-1 exactly once. */
    private static boolean isPermutation(List<Integer> indices, int size) {
        if (indices == null || indices.size() != size) {
            return false;
        }
        return indices.stream().allMatch(i -> i != null && i >= 0 && i < size)
                && new HashSet<>(indices).size() == size;
    }

    @SubscribeMapping("/{id}")
    public Playlist getPlaylist(Principal p, @DestinationVariable("id") int id) {
        return new PlaylistService.BroadcastedPlaylist(readablePlaylist(id, p.getName()), true);
    }

    @MessageMapping("/files/{id}")
    @SendToUser(broadcast = false)
    public List<MediaFileEntry> getPlaylistEntries(Principal p, @DestinationVariable("id") int id) {
        readablePlaylist(id, p.getName());
        return mediaFileService.toMediaFileEntryList(playlistService.getFilesInPlaylist(id, true), p.getName(), true, true, null, null, null);
    }

    public static class PlaylistFilesModificationRequest {
        private int id;
        private List<Integer> modifierIds;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public List<Integer> getModifierIds() {
            return modifierIds;
        }

        public void setModifierIds(List<Integer> modifierIds) {
            this.modifierIds = modifierIds;
        }
    }

    /**
     * @param added   the number of songs added to the playlist
     * @param allowed false if the playlist does not exist or the user may not change it
     */
    public record PlaylistFilesAddResult(int id, String name, int added, boolean allowed) {
    }

    public static class PlaylistUpdateRequest {
        private int id;
        private String name;
        private String comment;
        private boolean shared;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public boolean getShared() {
            return shared;
        }

        public void setShared(boolean shared) {
            this.shared = shared;
        }
    }
}
