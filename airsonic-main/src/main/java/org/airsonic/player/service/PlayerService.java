/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import com.google.common.util.concurrent.Striped;
import org.airsonic.player.command.PlayerSettingsCommand;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayerTechnology;
import org.airsonic.player.domain.TranscodeScheme;
import org.airsonic.player.domain.Transcoding;
import org.airsonic.player.domain.User;
import org.airsonic.player.repository.PlayerRepository;
import org.airsonic.player.repository.TranscodingRepository;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * Provides services for maintaining the set of players.
 *
 * @author Sindre Mehus
 * @see Player
 */
@Service
@DependsOn("liquibase")
public class PlayerService {

    private static final String COOKIE_NAME = "player";
    private static final int COOKIE_EXPIRY = 365 * 24 * 3600; // One year
    private static final Duration LAST_SEEN_RESOLUTION = Duration.ofMinutes(1);

    private static final Logger LOG = LoggerFactory.getLogger(PlayerService.class);

    @Autowired
    private StatusService statusService;
    @Autowired
    private TranscodingRepository transcodingRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private AsyncWebSocketClient asyncWebSocketClient;

    @EventListener
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        deleteOldPlayers(60);
    }

    private final Map<Integer, PlayQueue> playlists = new ConcurrentHashMap<>();

    private final Striped<Lock> playerLocks = Striped.lock(64);

    /**
     * Adds a playlist to the given player.
     * @param player The player to add the playlist to.
     */
    private void addPlaylist(Player player) {
        player.setPlayQueue(playlists.computeIfAbsent(player.getId(), k -> new PlayQueue()));
    }

    /**
     * Deletes all players that have not been seen for the given number of days.
     *
     * @param days The number of days.
     */
    private void deleteOldPlayers(int days) {
        LOG.info("Deleting old players");
        playerRepository.deleteAllByNameIsNullAndClientIdIsNullAndLastSeenIsNull();
        Instant lastSeen = Instant.now().minus(days, ChronoUnit.DAYS);
        playerRepository.deleteAllByNameIsNullAndClientIdIsNullAndLastSeenBefore(lastSeen);
        LOG.info("Complete Deleting old players");
    }

    public Player getPlayer(HttpServletRequest request, HttpServletResponse response, String username) throws Exception {
        return getPlayer(request, response, username, true, false);
    }

    public Player getPlayer(HttpServletRequest request, HttpServletResponse response, String username, boolean remoteControlEnabled,
            boolean isStreamRequest) throws Exception {
        return getPlayer(request, response, null, username, remoteControlEnabled, isStreamRequest);
    }

    public Player getPlayer(HttpServletRequest request, HttpServletResponse response,
            Integer playerId, String username, boolean remoteControlEnabled, boolean isStreamRequest) throws Exception {
        return getPlayer(request, response, playerId, username, request.getHeader("user-agent"), remoteControlEnabled, isStreamRequest, false);
    }

    /**
     * Returns the player associated with the given HTTP request.  If no such player exists, a new
     * one is created.
     *
     * @param request              The HTTP request.
     * @param response             The HTTP response.
     * @param playerId             The ID of the player to return. May be <code>null</code>.
     * @param username             The name of the current user. May be <code>null</code>.
     * @param userAgent            The user agent of the HTTP request.
     * @param remoteControlEnabled Whether this method should return a remote-controlled player.
     * @param isStreamRequest      Whether the HTTP request is a request for streaming data.
     * @param isWebSocketRequest   Whether the HTTP request is a request for a WebSocket.
     * @return The player associated with the given HTTP request. Never <code>null</code>.
     */
    public Player getPlayer(HttpServletRequest request, HttpServletResponse response,
            Integer playerId, String username, String userAgent, boolean remoteControlEnabled, boolean isStreamRequest, boolean isWebSocketRequest) throws Exception {
        // players are looked up and created per user and address, so only requests of the same client wait for each other
        Lock lock = playerLocks.get(username + "@" + request.getRemoteAddr());
        lock.lock();
        try {
            Player player = findRequestedPlayer(request, playerId, username, remoteControlEnabled, isWebSocketRequest);

            // Look for player with same IP address and user name.
            if (player == null) {
                player = getNonRestPlayerByIpAddressAndUsername(request.getRemoteAddr(), username);
            }

            player = saveSeenPlayer(player, username, request.getRemoteAddr(), userAgent, isStreamRequest);
            rememberPlayer(request, response, player, username, remoteControlEnabled && !isWebSocketRequest);
            return player;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the player the request names by ID, 'player' parameter, session or cookie, unless it belongs to another user.
     */
    private Player findRequestedPlayer(HttpServletRequest request, Integer playerId, String username,
            boolean remoteControlEnabled, boolean isWebSocketRequest) throws ServletRequestBindingException {
        Player player = getPlayerById(playerId);

        if (!isWebSocketRequest) {
            // Find by 'player' request parameter.
            if (player == null) {
                player = getPlayerById(ServletRequestUtils.getIntParameter(request, "player"));
            }
            // Find in session context.
            if (player == null && remoteControlEnabled) {
                player = getPlayerById((Integer) request.getSession().getAttribute("player"));
            }
            // Find by cookie.
            if (player == null && remoteControlEnabled) {
                player = getPlayerById(getPlayerIdFromCookie(request, username));
            }
        }

        // Make sure we're not hijacking the player of another user.
        if (player != null && player.getUsername() != null && username != null && !player.getUsername().equals(username)) {
            return null;
        }
        return player;
    }

    /**
     * Creates the player if none was found, otherwise stores what changed about it.
     */
    private Player saveSeenPlayer(Player player, String username, String remoteAddress, String userAgent, boolean isStreamRequest) {
        if (player == null) {
            Player newPlayer = new Player();
            newPlayer.setLastSeen(Instant.now());
            populatePlayer(newPlayer, username, remoteAddress, userAgent, isStreamRequest);
            return createPlayer(newPlayer);
        }
        if (populatePlayer(player, username, remoteAddress, userAgent, isStreamRequest)) {
            updatePlayer(player);
        }
        return player;
    }

    /**
     * Remembers the player in a cookie and, for remote-controlled players, in the session.
     */
    private void rememberPlayer(HttpServletRequest request, HttpServletResponse response, Player player, String username,
            boolean rememberInSession) {
        if (response != null) {
            String cookieName = COOKIE_NAME + "-" + StringUtil.utf8HexEncode(username);
            String path = request.getContextPath();
            ResponseCookie cookie = ResponseCookie.from(cookieName, String.valueOf(player.getId()))
                    .maxAge(COOKIE_EXPIRY)
                    .httpOnly(true)
                    .path(StringUtils.isEmpty(path) ? "/" : path)
                    .sameSite(SameSite.STRICT.attributeValue())
                    .build();
            response.addHeader("Set-Cookie", cookie.toString());
        }

        if (rememberInSession && request.getSession() != null) {
            request.getSession().setAttribute("player", player.getId());
        }
    }

    private boolean populatePlayer(Player player, String username, String remoteAddress, String userAgent, boolean isStreamRequest) {
        // Update player data.
        boolean isUpdate = false;
        if (username != null && player.getUsername() == null) {
            player.setUsername(username);
            isUpdate = true;
        }
        if (!Strings.CS.equals(remoteAddress, player.getIpAddress()) &&
                (player.getIpAddress() == null || isStreamRequest || (!isPlayerConnected(player) && player.getDynamicIp()))) {
            player.setIpAddress(remoteAddress);
            isUpdate = true;
        }
        if (isStreamRequest) {
            // every stream request would otherwise be a database write; lastSeen only needs to be roughly current
            Instant now = Instant.now();
            if (!Strings.CS.equals(userAgent, player.getType()) || player.getLastSeen() == null
                    || player.getLastSeen().isBefore(now.minus(LAST_SEEN_RESOLUTION))) {
                player.setType(userAgent);
                player.setLastSeen(now);
                isUpdate = true;
            }
        }

        return isUpdate;
    }

    /**
     * Updates the given player.
     *
     * @param player The player to update.
     */
    @Transactional
    public void updatePlayer(Player player) {
        playerRepository.save(player);
        if (player.getUsername() != null) {
            asyncWebSocketClient.sendToUser(player.getUsername(), "/queue/players/updated",
                    Map.of("id", player.getId(), "description", player.getShortDescription(), "tech", player.getTechnology()));
        }
    }

    /**
     * Returns the player with the given ID.
     *
     * @param id The unique player ID.
     * @return The player with the given ID, or <code>null</code> if no such player exists.
     */
    public Player getPlayerById(Integer id) {
        if (id == null) {
            return null;
        } else {
            Optional<Player> optPlayer = playerRepository.findById(id);
            optPlayer.ifPresent(p -> addPlaylist(p));
            return optPlayer.orElse(null);
        }
    }

    /**
     * Returns whether the given player is connected.
     *
     * @param player The player in question.
     * @return Whether the player is connected.
     */
    private boolean isPlayerConnected(Player player) {
        return !statusService.getStreamStatusesForPlayer(player).isEmpty();
    }

    /**
     * Returns the (non-REST) player with the given IP address and username. If no username is given, only IP address is
     * used as search criteria.
     *
     * @param ipAddress The IP address.
     * @param username  The remote user.
     * @return The player with the given IP address, or <code>null</code> if no such player exists.
     */
    private Player getNonRestPlayerByIpAddressAndUsername(final String ipAddress, final String username) {
        if (ipAddress == null) {
            return null;
        }
        for (Player player : getAllPlayers()) {
            boolean isRest = player.getClientId() != null;
            boolean ipMatches = ipAddress.equals(player.getIpAddress());
            boolean userMatches = username == null || username.equals(player.getUsername());
            if (!isRest && ipMatches && userMatches) {
                return player;
            }
        }
        return null;
    }

    /**
     * Reads the player ID from the cookie in the HTTP request.
     *
     * @param request  The HTTP request.
     * @param username The name of the current user.
     * @return The player ID embedded in the cookie, or <code>null</code> if cookie is not present.
     */
    private Integer getPlayerIdFromCookie(HttpServletRequest request, String username) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String cookieName = COOKIE_NAME + "-" + StringUtil.utf8HexEncode(username);
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                try {
                    return Integer.valueOf(cookie.getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Returns all players owned by the given username.
     *
     * @param username The name of the user. May be <code>null</code>.
     * @return All relevant players. Never <code>null</code>.
     */
    public List<Player> getPlayersForUser(String username) {
        if (username == null) {
            LOG.warn("Username is null");
            return new ArrayList<>();
        }
        List<Player> players = playerRepository.findByUsername(username);
        players.forEach(player -> addPlaylist(player));
        return players;
    }

    /**
     * Returns all players owned by the given username and client ID.
     *
     * @param username The name of the user.
     * @param clientId The third-party client ID (used if this player is managed over the
     *                 Airsonic REST API). May be <code>null</code>.
     * @return All relevant players.
     */
    public List<Player> getPlayersForUserAndClientId(String username, String clientId) {
        List<Player> players;
        if (clientId == null) {
            players = playerRepository.findByUsernameAndClientIdIsNull(username);
        } else {
            players = playerRepository.findByUsernameAndClientId(username, clientId);
        }
        players.forEach(player -> addPlaylist(player));
        return players;
    }

    /**
     * Returns all currently registered players.
     *
     * @return All currently registered players.
     */
    public List<Player> getAllPlayers() {
        List<Player> players = playerRepository.findAll();
        players.forEach(player -> addPlaylist(player));
        return players;
    }

    /**
     * Removes the player with the given ID.
     *
     * @param id The unique player ID.
     */
    @Transactional
    public void removePlayerById(int id) {
        playerRepository.findById(id).ifPresentOrElse(player -> {
            playlists.remove(id);
            playerRepository.delete(player);
            asyncWebSocketClient.send("/topic/players/deleted", id);
        },
            () -> {
                LOG.warn("Player with id {} not found", id);
            });
    }

    /**
     * Creates and returns a clone of the given player.
     *
     * @param playerId The ID of the player to clone.
     * @return The cloned player.
     */
    public Player clonePlayer(int playerId) {
        Player player = getPlayerById(playerId);

        // Clone player.
        Player clone = new Player();
        clone.setTechnology(player.getTechnology());
        clone.setClientId(player.getClientId());
        clone.setType(player.getType());
        clone.setUsername(player.getUsername());
        clone.setIpAddress(player.getIpAddress());
        clone.setDynamicIp(player.getDynamicIp());
        clone.setAutoControlEnabled(player.getAutoControlEnabled());
        clone.setM3uBomEnabled(player.getM3uBomEnabled());
        clone.setLastSeen(player.getLastSeen());
        clone.setTranscodeScheme(player.getTranscodeScheme());
        clone.setTranscodings(player.getTranscodings());
        if (player.getName() != null) {
            clone.setName(player.getName() + " (copy)");
        }
        return createPlayer(clone);
    }


    /**
     * Creates the given player, and activates all transcodings.
     *
     * @param player The player to create.
     */
    @Transactional
    public Player createPlayer(Player player) {

        // Set default transcodings.
        List<Transcoding> defaultActiveTranscodings = transcodingRepository.findByDefaultActiveTrue();

        // Save player.
        Player saved = playerRepository.save(player);
        // never Player 0 due to odd bug cataloged in
        // https://github.com/airsonic-advanced/airsonic-advanced/issues/646
        if (saved.getId() == 0) {
            LOG.info("Player 0 created, deleting and recreating");
            Player clone = new Player();
            clone.setName(player.getName());
            clone.setTechnology(player.getTechnology());
            clone.setClientId(player.getClientId());
            clone.setType(player.getType());
            clone.setUsername(player.getUsername());
            clone.setIpAddress(player.getIpAddress());
            clone.setDynamicIp(player.getDynamicIp());
            clone.setAutoControlEnabled(player.getAutoControlEnabled());
            clone.setM3uBomEnabled(player.getM3uBomEnabled());
            clone.setLastSeen(player.getLastSeen());
            clone.setTranscodeScheme(player.getTranscodeScheme());
            clone.setTranscodings(player.getTranscodings());
            saved = playerRepository.save(clone);
            playerRepository.delete(player);
        }
        saved.setTranscodings(defaultActiveTranscodings);
        playerRepository.save(saved);

        // Add player to playlist map.
        addPlaylist(saved);

        if (saved != null && saved.getUsername() != null) {
            asyncWebSocketClient.sendToUser(saved.getUsername(), "/queue/players/created",
                    Map.of("id", saved.getId(), "description", saved.getShortDescription(), "tech", saved.getTechnology()));
        }
        return saved;
    }

    /**
     * Returns a player associated to the special "guest" user, creating it if necessary.
     */
    public Player getGuestPlayer(String remoteAddress) {

        // Look for existing player.
        List<Player> players = getPlayersForUserAndClientId(User.USERNAME_GUEST, null);
        if (!players.isEmpty()) {
            return players.get(0);
        }

        // Create player if necessary.
        Player player = new Player();
        if (remoteAddress != null) {
            player.setIpAddress(remoteAddress);
        }
        player.setUsername(User.USERNAME_GUEST);
        return createPlayer(player);
    }

    /**
     * Updates the given player.
     * @param command The command to update the player with.
     * @return The updated player.
     */
    @Transactional
    public Player updateByCommand(PlayerSettingsCommand command) {
        return playerRepository.findById(command.getPlayerId()).map(player -> {
            String name = StringUtils.trimToNull(command.getName());
            player.setName(name);
            player.setLastSeen(command.getLastSeen());
            player.setDynamicIp(command.getDynamicIp());
            player.setAutoControlEnabled(command.getAutoControlEnabled());
            player.setTranscodeScheme(TranscodeScheme.valueOf(command.getTranscodeSchemeName()));
            player.setTechnology(PlayerTechnology.valueOf(command.getTechnologyName()));
            player.setTranscodings(transcodingRepository.findAllById(command.getActiveTranscodingIds()));
            playerRepository.save(player);
            addPlaylist(player);
            return player;
        }).orElse(null);
    }

    public void setStatusService(StatusService statusService) {
        this.statusService = statusService;
    }
}
