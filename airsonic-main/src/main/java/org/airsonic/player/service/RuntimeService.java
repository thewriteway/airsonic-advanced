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

 Copyright 2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */

package org.airsonic.player.service;

import org.springframework.stereotype.Service;

@Service
public class RuntimeService {

    /**
     * Returns the total amount of memory in the Java Virtual Machine.
     * @return the total amount of memory in the Java Virtual Machine.
     */
    public long getTotalMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    /**
     * Returns the amount of free memory in the Java Virtual Machine.
     * @return the amount of free memory in the Java Virtual Machine.
     */
    public long getFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    /**
     * Returns the amount of memory currently used by the Java Virtual Machine.
     * @return the amount of memory currently used by the Java Virtual Machine.
     */
    public long getUsedMemory() {
        return getTotalMemory() - getFreeMemory();
    }

}
