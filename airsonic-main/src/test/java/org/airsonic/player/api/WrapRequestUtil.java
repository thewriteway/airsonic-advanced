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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.api;

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayerTechnology;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WrapRequestUtil {

    public static void assertRestAPIPlayer(
        Player player,
        String expectedClientId,
        String expectedUsername
    ) {
        assertEquals(expectedClientId, player.getClientId());
        assertEquals(expectedUsername, player.getUsername());
        assertEquals(expectedClientId, player.getName());
        assertEquals(PlayerTechnology.EXTERNAL_WITH_PLAYLIST, player.getTechnology());
    }
}
