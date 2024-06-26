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
package org.airsonic.player.domain;

import org.junit.jupiter.api.Test;

import static org.airsonic.player.domain.TranscodeScheme.*;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit test of {@link TranscodeScheme}.
 *
 * @author Sindre Mehus
 */
public class TranscodeSchemeTestCase {

    /**
     * Tests {@link TranscodeScheme#strictest}.
     */
    @Test
    public void testStrictest() {
        assertSame(OFF, OFF.strictest(null), "Error in strictest()");
        assertSame(OFF, OFF.strictest(OFF), "Error in strictest()");
        assertSame(MAX_32, OFF.strictest(MAX_32), "Error in strictest()");
        assertSame(MAX_32, MAX_32.strictest(null), "Error in strictest()");
        assertSame(MAX_32, MAX_32.strictest(OFF), "Error in strictest()");
        assertSame(MAX_32, MAX_32.strictest(MAX_64), "Error in strictest()");
        assertSame(MAX_32, MAX_64.strictest(MAX_32), "Error in strictest()");
    }
}
