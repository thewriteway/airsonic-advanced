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

package org.airsonic.player.ajax;

import java.util.UUID;

/**
 * Details about what a user is currently listening to.
 *
 * @author Sindre Mehus
 */
public class NowPlayingInfo {

    private final UUID transferId;
    private final Integer playerId;
    private final Integer mediaFileId;

    /**
     * Constructs a new NowPlayingInfo object.
     *
     * @param transferId the unique identifier for the transfer
     * @param playerId the ID of the player
     * @param mediaFileId the ID of the media file being played
     */
    public NowPlayingInfo(UUID transferId, Integer playerId, Integer mediaFileId) {
        this.transferId = transferId;
        this.playerId = playerId;
        this.mediaFileId = mediaFileId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public Integer getMediaFileId() {
        return mediaFileId;
    }

}
