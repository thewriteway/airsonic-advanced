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
package org.airsonic.player.command;

import org.airsonic.player.controller.PlayerSettingsController;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayerTechnology;
import org.airsonic.player.domain.TranscodeScheme;
import org.airsonic.player.domain.Transcoding;

import java.time.Instant;
import java.util.List;

/**
 * Command used in {@link PlayerSettingsController}.
 *
 * @author Sindre Mehus
 */
public class PlayerSettingsCommand {
    private Integer playerId;
    private String name;
    private String description;
    private String type;
    private Instant lastSeen;
    private boolean dynamicIp;
    private boolean autoControlEnabled;
    private boolean m3uBomEnabled;
    private String technologyName;
    private String transcodeSchemeName;
    private boolean transcodingSupported;
    private String transcodeDirectory;
    private List<Transcoding> allTranscodings;
    private List<Integer> activeTranscodingIds;
    private EnumHolder[] technologyHolders;
    private EnumHolder[] transcodeSchemeHolders;
    private Player[] players;
    private boolean isAdmin;

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean getDynamicIp() {
        return dynamicIp;
    }

    public void setDynamicIp(boolean dynamicIp) {
        this.dynamicIp = dynamicIp;
    }

    public boolean getAutoControlEnabled() {
        return autoControlEnabled;
    }

    public void setAutoControlEnabled(boolean autoControlEnabled) {
        this.autoControlEnabled = autoControlEnabled;
    }

    public boolean getM3uBomEnabled() {
        return m3uBomEnabled;
    }

    public void setM3uBomEnabled(boolean m3uBomEnabled) {
        this.m3uBomEnabled = m3uBomEnabled;
    }

    public String getTranscodeSchemeName() {
        return transcodeSchemeName;
    }

    public void setTranscodeSchemeName(String transcodeSchemeName) {
        this.transcodeSchemeName = transcodeSchemeName;
    }

    public boolean isTranscodingSupported() {
        return transcodingSupported;
    }

    public void setTranscodingSupported(boolean transcodingSupported) {
        this.transcodingSupported = transcodingSupported;
    }

    public String getTranscodeDirectory() {
        return transcodeDirectory;
    }

    public void setTranscodeDirectory(String transcodeDirectory) {
        this.transcodeDirectory = transcodeDirectory;
    }

    public List<Transcoding> getAllTranscodings() {
        return allTranscodings;
    }

    public void setAllTranscodings(List<Transcoding> allTranscodings) {
        this.allTranscodings = allTranscodings;
    }

    public List<Integer> getActiveTranscodingIds() {
        return activeTranscodingIds;
    }

    public void setActiveTranscodingIds(List<Integer> activeTranscodingIds) {
        this.activeTranscodingIds = activeTranscodingIds;
    }

    public EnumHolder[] getTechnologyHolders() {
        return technologyHolders;
    }

    public void setTechnologies(PlayerTechnology[] technologies) {
        technologyHolders = new EnumHolder[technologies.length];
        for (int i = 0; i < technologies.length; i++) {
            PlayerTechnology technology = technologies[i];
            technologyHolders[i] = new EnumHolder(technology.name(), technology.toString());
        }
    }

    public EnumHolder[] getTranscodeSchemeHolders() {
        return transcodeSchemeHolders;
    }

    public void setTranscodeSchemes(TranscodeScheme[] transcodeSchemes) {
        transcodeSchemeHolders = new EnumHolder[transcodeSchemes.length];
        for (int i = 0; i < transcodeSchemes.length; i++) {
            TranscodeScheme scheme = transcodeSchemes[i];
            transcodeSchemeHolders[i] = new EnumHolder(scheme.name(), scheme.toString());
        }
    }

    public String getTechnologyName() {
        return technologyName;
    }

    public void setTechnologyName(String technologyName) {
        this.technologyName = technologyName;
    }

    public Player[] getPlayers() {
        return players;
    }

    public void setPlayers(Player[] players) {
        this.players = players;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    /**
     * Holds the transcoding and whether it is active for the given player.
     */
    public static class TranscodingHolder {
        private Transcoding transcoding;
        private boolean isActive;

        public TranscodingHolder(Transcoding transcoding, boolean isActive) {
            this.transcoding = transcoding;
            this.isActive = isActive;
        }

        public Transcoding getTranscoding() {
            return transcoding;
        }

        public boolean isActive() {
            return isActive;
        }
    }
}
