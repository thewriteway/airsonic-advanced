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
 * Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.config;

import org.airsonic.player.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


@ConfigurationProperties
public class AirsonicHomeConfig {

    // Logger
    private static final Logger LOG = LoggerFactory.getLogger(AirsonicHomeConfig.class);

    private final Path AIRSONIC_HOME_WINDOWS = Paths.get("c:/airsonic");
    private final Path AIRSONIC_HOME_OTHER = Paths.get("/var/airsonic");


    @Value("${airsonic.home:}")
    private String airsonicHome;
    @Value("${libresonic.home:}")
    private String libresonicHome;

    public AirsonicHomeConfig() {}

    /**
     * Constructor.
     *
     * @param airsonicHome airsonic.home directory path
     * @param libresonicHome  libresonic.home directory path deprecated
     */
    public AirsonicHomeConfig(
        String airsonicHome,
        String libresonicHome) {
        this.airsonicHome = airsonicHome;
        this.libresonicHome = libresonicHome;
        ensureDirectoryPresent();
    }

    private Path getAirsonicHomePath() {
        // Airsonic home directory.
        Path home = Util.isWindows() ? AIRSONIC_HOME_WINDOWS : AIRSONIC_HOME_OTHER;
        if (StringUtils.hasText(this.airsonicHome)) {
            // If the home directory is explicitly set, use it.
            home = Paths.get(this.airsonicHome);
        } else if (StringUtils.hasText(this.libresonicHome)) {
            // libresonic.home is deprecated. If it is set, use it.
            LOG.warn("libresonic.home is deprecated. Please use airsonic.home instead.");
            home = Paths.get(this.libresonicHome);
        }
        return home;
    }

    /**
     * Returns the home directory for Airsonic.
     *
     * @return The home directory. Never {@code null}.
     */
    public Path getAirsonicHome() {
        Path home = getAirsonicHomePath();
        ensureDirectoryPresent();
        return home;
    }

    /**
     * Returns the default log file.
     *
     * @return The default log file. Never {@code null}.
     */
    public String getDefaultLogFile() {
        return getAirsonicHome().resolve(getFileSystemAppName() + ".log").toString();
    }

    /**
     * Returns the default database URL.
     *
     * @return The default database URL. Never {@code null}.
     */
    public String getDefaultJDBCUrl() {
        return "jdbc:hsqldb:file:" + getAirsonicHome().resolve("db").resolve(getFileSystemAppName()).toString() + ";hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false;sql.pad_space=false;hsqldb.defrag_limit=50;hsqldb.default_table_type=CACHED;shutdown=true";
    }

    /**
     * Returns the name of the file system app.
     *
     * @return The name of the file system app. Never {@code null}.
     */
    private String getFileSystemAppName() {
        String home = getAirsonicHome().toString();
        return home.contains("libresonic") ? "libresonic" : "airsonic";
    }

    /**
     * get the property file path
     *
     * @return the property file path
     */
    public Path getPropertyFile() {
        return getAirsonicHome().resolve(getFileSystemAppName() + ".properties");
    }

    /**
     * Ensure that the airsonic home directory is present.
     * If not, create it.
     */
    @PostConstruct
    public void ensureDirectoryPresent() {
        Path home = getAirsonicHomePath();
        if (!Files.exists(home)) {
            try {
                Files.createDirectory(home);
            } catch (Exception e) {
                LOG.error("Failed to create or see airsonic home directory. {}", home, e);
                String message = "The directory " + home + " does not exist. Please create it and make it writable. " +
                        "(You can override the directory location by specifying -Dairsonic.home=... when " +
                        "starting the servlet container.)";
                throw new RuntimeException(message);
            }
        }
    }

    /**
     * Returns the directory in which all transcoders are installed.
     *
     * @return The transcoder directory. Never {@code null}.
     */
    public Path getTranscodeDirectory() {
        Path dir = getAirsonicHome().resolve("transcode");
        if (!Files.exists(dir)) {
            try {
                dir = Files.createDirectory(dir);
                LOG.info("Created directory {}", dir);
            } catch (Exception e) {
                LOG.warn("Failed to create directory {}", dir);
            }
        }
        return dir;
    }
}
