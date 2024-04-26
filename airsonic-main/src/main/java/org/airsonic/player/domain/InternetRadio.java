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

 Copyright 2023 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Represents an internet radio station.
 *
 * @author Sindre Mehus
 * @version $Revision: 1.2 $ $Date: 2005/12/25 13:48:46 $
 */
@Entity
@Table(name = "internet_radio")
public class InternetRadio {

    // The system-generated ID.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // The user-defined name.
    @Column(name = "name", nullable = false)
    private String name;

    // The stream URL for the station.
    @Column(name = "stream_url", nullable = false)
    private String streamUrl;

    // The home page URL for the station.
    @Column(name = "homepage_url")
    private String homepageUrl;

    // Whether the station is enabled.
    @Column(name = "enabled")
    private boolean enabled;

    // When the corresponding database entry was last changed.
    @Column(name = "changed", nullable = false)
    private Instant changed;

    public InternetRadio() {
    }

    /**
     * Creates a new internet radio station.
     *
     * @param name      The user-defined name.
     * @param streamUrl  The stream URL for the station.
     * @param homepageUrl The home page URL for the station.
     * @param enabled Whether the station is enabled.
     * @param changed   When the corresponding database entry was last changed.
     */
    public InternetRadio(String name, String streamUrl, String homepageUrl, boolean enabled, Instant changed) {
        this.name = name;
        this.streamUrl = streamUrl;
        this.homepageUrl = homepageUrl;
        this.enabled = enabled;
        this.changed = changed;
    }

    /**
     * Returns the system-generated ID.
     *
     * @return The system-generated ID.
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the user-defined id.
     *
     * @param id The user-defined id.
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Returns the user-defined name.
     *
     * @return The user-defined name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user-defined name.
     *
     * @param name The user-defined name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the stream URL of the radio station.
     *
     * @return The stream URL of the radio station.
     */
    public String getStreamUrl() {
        return streamUrl;
    }

    /**
     * Sets the stream URL of the radio station.
     *
     * @param streamUrl The stream URL of the radio station.
     */
    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    /**
     * Returns the homepage URL of the radio station.
     *
     * @return The homepage URL of the radio station.
     */
    public String getHomepageUrl() {
        return homepageUrl;
    }

    /**
     * Sets the home page URL of the radio station.
     *
     * @param homepageUrl The home page URL of the radio station.
     */
    public void setHomepageUrl(String homepageUrl) {
        this.homepageUrl = homepageUrl;
    }

    /**
     * Returns whether the radio station is enabled.
     *
     * @return Whether the radio station is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the radio station is enabled.
     *
     * @param enabled Whether the radio station is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns when the corresponding database entry was last changed.
     *
     * @return When the corresponding database entry was last changed.
     */
    public Instant getChanged() {
        return changed;
    }

    /**
     * Sets when the corresponding database entry was last changed.
     *
     * @param changed When the corresponding database entry was last changed.
     */
    public void setChanged(Instant changed) {
        this.changed = changed;
    }
}