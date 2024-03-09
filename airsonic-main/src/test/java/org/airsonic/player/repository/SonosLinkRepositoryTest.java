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
 */
package org.airsonic.player.repository;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.SonosLink;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnableConfigurationProperties({ AirsonicHomeConfig.class })
@Transactional
public class SonosLinkRepositoryTest {

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @Autowired
    private SonosLinkRepository sonosLinkRepository;

    @Test
    public void testSave() {
        SonosLink sonosLink = new SonosLink("username", "linkcode", "householdId", "initiator", Instant.now());
        sonosLinkRepository.saveAndFlush(sonosLink);
        Optional<SonosLink> sonosLinkOptional = sonosLinkRepository.findById("linkcode");
        assertTrue(sonosLinkOptional.isPresent());
        assertEquals(sonosLinkOptional.get(), sonosLink);
    }
}
