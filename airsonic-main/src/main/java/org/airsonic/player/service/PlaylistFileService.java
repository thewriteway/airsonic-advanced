package org.airsonic.player.service;

import chameleon.playlist.SpecificPlaylist;
import chameleon.playlist.SpecificPlaylistFactory;
import chameleon.playlist.SpecificPlaylistProvider;
import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.User;
import org.airsonic.player.repository.UserRepository;
import org.airsonic.player.service.playlist.PlaylistExportHandler;
import org.airsonic.player.service.playlist.PlaylistImportHandler;
import org.airsonic.player.util.StringUtil;
import org.airsonic.player.util.UserUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.*;
import java.util.stream.Stream;

@Service
public class PlaylistFileService {

    private static final Logger LOG = LoggerFactory.getLogger(PlaylistFileService.class);
    // 0-100: minimum confidence before a detected charset is trusted over UTF-8
    private static final int CHARSET_DETECTION_THRESHOLD = 35;

    private final PlaylistService playlistService;
    private final SettingsService settingsService;
    private final UserRepository userRepository;
    private final PathWatcherService pathWatcherService;
    private final PlaylistImportThrottle importThrottle;
    private final List<PlaylistExportHandler> exportHandlers;
    private final List<PlaylistImportHandler> importHandlers;

    public PlaylistFileService(PlaylistService playlistService,
                               SettingsService settingsService,
                               UserRepository userRepository,
                               PathWatcherService pathWatcherService,
                               PlaylistImportThrottle importThrottle,
                               List<PlaylistExportHandler> exportHandlers,
                               List<PlaylistImportHandler> importHandlers) {
        this.playlistService = playlistService;
        this.settingsService = settingsService;
        this.userRepository = userRepository;
        this.pathWatcherService = pathWatcherService;
        this.importThrottle = importThrottle;
        this.exportHandlers = exportHandlers;
        this.importHandlers = importHandlers;
    }


    @EventListener
    public void init(ApplicationStartedEvent event) throws IOException {
        addPlaylistFolderWatcher();
    }

    public void addPlaylistFolderWatcher() {
        Path playlistFolder = Paths.get(settingsService.getPlaylistFolder());
        if (Files.exists(playlistFolder) && Files.isDirectory(playlistFolder)) {
            try {
                pathWatcherService.setWatcher("Playlist folder watcher", playlistFolder,
                        this::handleModifiedPlaylist, null, this::handleModifiedPlaylist, null);
            } catch (Exception e) {
                LOG.warn("Issues setting watcher for folder: {}", playlistFolder);
            }
        }
    }

    private void handleModifiedPlaylist(Path path, WatchEvent<Path> event) {
        Path fullPath = path.resolve(event.context());
        // Coalesce filesystem-event bursts for the same path and bound concurrent imports
        // against the HikariCP pool (issue #215). The Runnable evaluates
        // getAllPlaylists() only when the debounce window elapses and a permit is
        // acquired, so a 35,000-event rsync becomes at most one DB-fetch + one import
        // per distinct file.
        importThrottle.submit(fullPath,
                () -> importPlaylist(fullPath, playlistService.getAllPlaylists()));
    }

    public void importPlaylists() {
        try {
            LOG.info("Starting playlist import.");
            doImportPlaylists();
            LOG.info("Completed playlist import.");
        } catch (Exception x) {
            LOG.warn("Failed to import playlists", x);
        }
    }

    private void doImportPlaylists() {
        String playlistFolderPath = settingsService.getPlaylistFolder();
        if (playlistFolderPath == null) {
            return;
        }
        Path playlistFolder = Paths.get(playlistFolderPath);
        if (!Files.exists(playlistFolder)) {
            return;
        }

        List<Playlist> allPlaylists = playlistService.getAllPlaylists();
        try (Stream<Path> children = Files.walk(playlistFolder)) {
            children.forEach(f -> importPlaylist(f, allPlaylists));
        } catch (IOException ex) {
            LOG.warn("Error while reading directory {} when importing playlists", playlistFolder, ex);
        }
    }

    private void importPlaylist(Path f, List<Playlist> allPlaylists) {
        if (Files.isRegularFile(f) && Files.isReadable(f)) {
            try {
                Playlist playlist = allPlaylists.stream()
                        .filter(p -> f.getFileName().toString().equals(p.getImportedFrom()))
                        .findAny().orElse(null);
                importPlaylistIfUpdated(f, playlist);
            } catch (Exception x) {
                LOG.warn("Failed to auto-import playlist {}", f, x);
            }
        }
    }

    /**
     * Import a playlist from a file.
     *
     * @param user        user importing the playlist
     * @param playlistName name of the playlist
     * @param fileName   name of the file
     * @param file     path to the file
     * @param inputStream  input stream to the file
     * @param existingPlaylist existing playlist to update, or null to create a new one
     * @return the imported playlist
     * @throws Exception
     */
    public Playlist importPlaylist(User user, String playlistName, String fileName, Path file, InputStream inputStream, Playlist existingPlaylist) throws Exception {

        // .m3u files are often Latin-1/Windows-1252 rather than UTF-8, so detect the encoding
        BufferedInputStream bufferedInput = new BufferedInputStream(inputStream);
        String encoding = detectEncoding(bufferedInput, fileName);
        final SpecificPlaylist inputSpecificPlaylist = SpecificPlaylistFactory.getInstance().readFrom(bufferedInput, encoding);
        if (inputSpecificPlaylist == null) {
            throw new Exception("Unsupported playlist " + fileName);
        }
        PlaylistImportHandler importHandler = getImportHandler(inputSpecificPlaylist);
        LOG.debug("Using {} playlist import handler", importHandler.getClass().getSimpleName());

        Pair<List<MediaFile>, List<String>> result = importHandler.handle(inputSpecificPlaylist, file);

        if (result.getLeft().isEmpty() && !result.getRight().isEmpty()) {
            throw new Exception("No songs in the playlist were found.");
        }

        for (String error : result.getRight()) {
            LOG.warn("File in playlist '{}' not found: {}", fileName, error);
        }
        if (existingPlaylist == null) {
            Playlist playlist = new Playlist();
            playlist.setUsername(user.getUsername());
            playlist.setShared(true);
            playlist.setName(playlistName);
            playlist.setComment("Auto-imported from " + fileName);
            playlist.setImportedFrom(fileName);
            Playlist savedPlaylist = playlistService.createPlaylist(playlist);
            playlistService.broadcast(savedPlaylist);
            savedPlaylist = playlistService.setFilesInPlaylist(savedPlaylist.getId(), result.getLeft());
            playlistService.broadcastFileChange(savedPlaylist.getId(), true, true);
            return savedPlaylist;
        } else {
            playlistService.setFilesInPlaylist(existingPlaylist.getId(), result.getLeft());
            playlistService.broadcastFileChange(existingPlaylist.getId(), existingPlaylist.getShared(), true);
            return existingPlaylist;
        }
    }

    private void importPlaylistIfUpdated(Path file, Playlist existingPlaylist) throws Exception {
        String fileName = file.getFileName().toString();
        if (existingPlaylist != null && Files.getLastModifiedTime(file).toMillis() <= existingPlaylist.getChanged().toEpochMilli()) {
            // Already imported and not changed since.
            return;
        }

        User sysAdmin = UserUtil.getSysAdmin(userRepository.findAll());
        if (sysAdmin == null) {
            LOG.error("No admin user found, skipping auto-import of playlist {}", file);
            return;
        }

        try (InputStream in = Files.newInputStream(file)) {
            importPlaylist(sysAdmin, FilenameUtils.getBaseName(fileName), fileName, null, in, existingPlaylist);
            LOG.info("Auto-imported playlist {}", file);
        }
    }

    public String getExportPlaylistExtension() {
        String format = settingsService.getPlaylistExportFormat();
        SpecificPlaylistProvider provider = SpecificPlaylistFactory.getInstance().findProviderById(format);
        return provider.getContentTypes()[0].getExtensions()[0];
    }

    public void exportPlaylist(int id, OutputStream out) throws Exception {
        String format = settingsService.getPlaylistExportFormat();
        SpecificPlaylistProvider provider = SpecificPlaylistFactory.getInstance().findProviderById(format);
        PlaylistExportHandler handler = getExportHandler(provider);
        SpecificPlaylist specificPlaylist = handler.handle(id, provider);
        specificPlaylist.writeTo(out, StringUtil.ENCODING_UTF8);
    }

    /**
     * Guesses the character encoding of a playlist, falling back to UTF-8.
     *
     * @param in       stream supporting mark/reset; it is reset to its start afterwards
     * @param fileName playlist file name, for logging
     * @return the Java charset name to decode the playlist with
     */
    private static String detectEncoding(BufferedInputStream in, String fileName) {
        try {
            CharsetMatch match = new CharsetDetector().setText(in).detect();
            if (match != null && match.getConfidence() > CHARSET_DETECTION_THRESHOLD) {
                LOG.debug("Detected charset {} for playlist {}", match.getName(), fileName);
                return match.getName();
            }
        } catch (IOException e) {
            LOG.debug("Failed to detect charset for playlist {}, using UTF-8", fileName, e);
        }
        return StandardCharsets.UTF_8.name();
    }

    private PlaylistImportHandler getImportHandler(SpecificPlaylist playlist) {
        return importHandlers.stream()
                .filter(handler -> handler.canHandle(playlist.getClass()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No import handler for " + playlist.getClass().getName()));

    }

    private PlaylistExportHandler getExportHandler(SpecificPlaylistProvider provider) {
        return exportHandlers.stream()
                .filter(handler -> handler.canHandle(provider.getClass()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No export handler for " + provider.getClass().getName()));
    }
}