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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
public class AirsonicScanConfigTest {

    @Nested
    @EnableConfigurationProperties(AirsonicScanConfig.class)
    @ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
    public class AirsonicScanConfigTestWithDefaultValue {

        @Autowired
        private AirsonicScanConfig scanConfig;

        private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        @Test
        public void testFullTimeoutProperty() {
            Integer expectedFullTimeout = 14400;
            assertEquals(expectedFullTimeout, scanConfig.getFullTimeout());
        }

        @Test
        public void testTimeoutProperty() {
            Integer expectedTimeout = 3600;
            assertEquals(expectedTimeout, scanConfig.getTimeout());
        }

        @Test
        public void testParallelismProperty() {
            Integer expectedParallelism = Runtime.getRuntime().availableProcessors() + 1;
            assertEquals(expectedParallelism, scanConfig.getParallelism());
        }

        @Test
        public void testInvalidProperties() {
            AirsonicScanConfig invalidConfig = new AirsonicScanConfig();
            invalidConfig.setFullTimeout(-100);
            invalidConfig.setTimeout(-100);
            invalidConfig.setParallelism(-100);

            Set<ConstraintViolation<AirsonicScanConfig>> violations = validator.validate(invalidConfig);
            assertEquals(3, violations.size());

            for (ConstraintViolation<AirsonicScanConfig> violation : violations) {
                assertTrue(violation.getPropertyPath().toString().matches("fullTimeout|timeout|parallelism"));
            }
        }
    }

    @Nested
    @EnableConfigurationProperties(AirsonicScanConfig.class)
    @ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
    @TestPropertySource(properties = {
        "MediaScannerParallelism=10"
    })
    public class AirsonicScanConfigTestWithDeprecatedParameters {

        @BeforeEach
        public void setup() {
            System.setProperty("MediaScannerParallelism", "10");
        }

        @AfterEach
        public void teardown() {
            System.clearProperty("MediaScannerParallelism");
        }

        @Autowired
        private AirsonicScanConfig scanConfig;

        @Test
        public void testParallelismProperty() {
            Integer expectedParallelism = 10;
            assertEquals(expectedParallelism, scanConfig.getParallelism());
        }
    }

    @Nested
    public class AirsonicScanConfigTestWithCustomValue {

        private AirsonicScanConfig scanConfig = new AirsonicScanConfig();

        @BeforeEach
        public void setup() {
            scanConfig.setFullTimeout(100);
            scanConfig.setTimeout(10);
            scanConfig.setParallelism(5);
        }

        @Test
        public void testFullTimeoutProperty() {
            Integer expectedFullTimeout = 100;
            assertEquals(expectedFullTimeout, scanConfig.getFullTimeout());
        }

        @Test
        public void testTimeoutProperty() {
            Integer expectedTimeout = 10;
            assertEquals(expectedTimeout, scanConfig.getTimeout());
        }

        @Test
        public void testParallelismProperty() {
            Integer expectedParallelism = 5;
            assertEquals(expectedParallelism, scanConfig.getParallelism());
        }
    }



}
