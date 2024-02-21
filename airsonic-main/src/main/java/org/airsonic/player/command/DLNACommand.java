/*
 * This file is part of Airsonic.
 *
 * Airsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Airsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyrifht 2024 (C) Y.Tory
 */
package org.airsonic.player.command;

public class DLNACommand {

    private boolean dlnaEnabled;
    private String dlnaServerName;
    private String dlnaBaseLANURL;

    public DLNACommand() {
    }

    public DLNACommand(boolean dlnaEnabled, String dlnaServerName, String dlnaBaseLANURL) {
        this.dlnaEnabled = dlnaEnabled;
        this.dlnaServerName = dlnaServerName;
        this.dlnaBaseLANURL = dlnaBaseLANURL;
    }

    public boolean isDlnaEnabled() {
        return dlnaEnabled;
    }

    public void setDlnaEnabled(boolean dlnaEnabled) {
        this.dlnaEnabled = dlnaEnabled;
    }

    public String getDlnaServerName() {
        return dlnaServerName;
    }

    public void setDlnaServerName(String dlnaServerName) {
        this.dlnaServerName = dlnaServerName;
    }

    public String getDlnaBaseLANURL() {
        return dlnaBaseLANURL;
    }

    public void setDlnaBaseLANURL(String dlnaBaseLANURL) {
        this.dlnaBaseLANURL = dlnaBaseLANURL;
    }

}
