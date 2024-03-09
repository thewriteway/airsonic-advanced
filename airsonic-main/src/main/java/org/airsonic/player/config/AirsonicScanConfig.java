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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

import java.util.Objects;

@ConfigurationProperties(prefix = "airsonic.scan")
@Validated
public class AirsonicScanConfig {

    // Logger
    private static final Logger LOG = LoggerFactory.getLogger(AirsonicHomeConfig.class);

    private static final int DEFAULT_SCAN = 60 * 60;
    private static final int DEFAULT_FULLSCAN = 4 * 60 * 60;

    @Positive
    private final Integer fullTimeout;

    @Positive
    private final Integer timeout;

    @Positive
    private final Integer parallelism;

    /**
     *
     * @param fullscan full scan timeout in seconds. null means default value
     * @param scan scan timeout in seconds. null means default value
     */
    public AirsonicScanConfig(
        Integer fullTimeout,
        Integer timeout,
        Integer parallelism) {
        this.fullTimeout = Objects.nonNull(fullTimeout) ? fullTimeout : DEFAULT_FULLSCAN;
        this.timeout = Objects.nonNull(timeout) ? timeout : DEFAULT_SCAN;
        this.parallelism = parallelism;
    }

    public Integer getFullTimeout() {
        return fullTimeout;
    }

    public Integer getTimeout() {
        return timeout;
    }

    /**
     * Get parallelism. If not set, use availableProcessors + 1
     *
     * @return parallelism
     */
    public Integer getParallelism() {
        if (Objects.nonNull(parallelism)) {
            return parallelism;
        }
        String deprecatedParallelism = System.getProperty("MediaScannerParallelism");
        if (deprecatedParallelism != null) {
            LOG.info("MediaScannerParallelism is deprecated. Use AIRSONC_SCAN_PARALLELISM instead.");
            return Integer.parseInt(deprecatedParallelism);
        }
        return Runtime.getRuntime().availableProcessors() + 1;
    }
}
