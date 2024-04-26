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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
  Unit test of {@link Version}.
  @author Sindre Mehus
 */

public class VersionTestCase {

    /**
     * Tests that equals(), hashCode(), toString() and compareTo() works.
     */
    @Test
    public void testVersion() {
        doTestVersion("0.0", "0.1");
        doTestVersion("1.5", "2.3");
        doTestVersion("2.3", "2.34");

        doTestVersion("1.5", "1.5.1");
        doTestVersion("1.5.1", "1.5.2");
        doTestVersion("1.5.2", "1.5.11");

        doTestVersion("1.4", "1.5.beta1");
        doTestVersion("1.4.1", "1.5.beta1");
        doTestVersion("1.5.beta1", "1.5");
        doTestVersion("1.5.beta1", "1.5.1");
        doTestVersion("1.5.beta1", "1.6");
        doTestVersion("1.5.beta1", "1.5.beta2");
        doTestVersion("1.5.beta2", "1.5.beta11");

        doTestVersion("6.2-SNAPSHOT", "6.11-SNAPSHOT");

        // internal stable comparisons
        doTestVersion("10.6.0.20200424173747", "10.6.0.20200424173748");
        doTestVersion("10.6.0.20200424173747", "11.0.0.20200424173747");
        // internal stable vs external stable
        doTestVersion("10.6.0", "10.6.0.20200424173747"); // should be the same
        doTestVersion("10.6.0.20200424173747", "11.0.0");
        // internal stable vs external snapshots
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "10.6.0.20200424173747");
        doTestVersion("10.6.0.20200424173747", "11.0.0-SNAPSHOT.20200424173747");
        // compare internal snapshot with external stable
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "10.6.0");
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "11.0.0");
        // compare internal snapshot with external snapshot
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "10.6.0-SNAPSHOT.20200424173748");
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "11.0.0-SNAPSHOT.20200424173747");
    }

    @Test
    public void testIsPreview() {
        Version version = new Version("1.6.0-SNAPSHOT");
        assertTrue(version.isPreview(), "Version should be snapshot");

        version = new Version("1.6.0-SNAPSHOT.20200424173747");
        assertTrue(version.isPreview(), "Version should be snapshot");

        version = new Version("1.6.0-beta2");
        assertTrue(version.isPreview(), "Version should be snapshot");

        version = new Version("1.6.0");
        assertFalse(version.isPreview(), "Version should not be snapshot");

        version = new Version("1.6.0-RELEASE");
        assertFalse(version.isPreview(), "Version should not be snapshot");
    }

    /**
     * Tests that equals(), hashCode(), toString() and compareTo() works.
     *
     * @param v1 A lower version.
     * @param v2 A higher version.
     */
    private void doTestVersion(String v1, String v2) {
        Version ver1 = new Version(v1);
        Version ver2 = new Version(v2);

        assertEquals(v1, ver1.toString(), "Error in toString().");
        assertEquals(v2, ver2.toString(), "Error in toString().");

        assertEquals(ver1, ver1, "Error in equals().");

        assertEquals(0, ver1.compareTo(ver1), "Error in compareTo().");
        assertEquals(0, ver2.compareTo(ver2), "Error in compareTo().");
        assertTrue(ver1.compareTo(ver2) < 0, "Error in compareTo().");
        assertTrue(ver2.compareTo(ver1) > 0, "Error in compareTo().");
    }
}