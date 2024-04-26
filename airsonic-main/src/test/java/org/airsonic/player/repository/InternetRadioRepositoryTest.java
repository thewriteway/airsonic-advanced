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
package org.airsonic.player.repository;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.InternetRadio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of {@link InternetRadioRepository}.
 *
 * @author Sindre Mehus
 */
@Transactional
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class InternetRadioRepositoryTest {

    @Autowired
    private InternetRadioRepository internetRadioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void setUp() {
        jdbcTemplate.execute("delete from internet_radio");
    }

    @Test
    public void testFindAllByEnabled() {

        // given
        InternetRadio radio = new InternetRadio("name", "streamUrl", "homePageUrl", true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        InternetRadio radio2 = new InternetRadio("name2", "streamUrl2", "homePageUrl2", true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        InternetRadio radioDisabled = new InternetRadio("name3", "streamUrl3", "homePageUrl3", false,
                Instant.now().truncatedTo(ChronoUnit.MICROS));

        internetRadioRepository.save(radio);
        internetRadioRepository.save(radio2);
        internetRadioRepository.save(radioDisabled);

        // when
        List<InternetRadio> radios = internetRadioRepository.findAllByEnabled(true);

        // then
        assertEquals(2, radios.size());
        assertTrue(radios.contains(radio));
        assertTrue(radios.contains(radio2));

    }

    @Test
    public void testFindByIdAndEnabledTrue() {
        // given
        InternetRadio radio = new InternetRadio("name", "streamUrl", "homePageUrl", true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        InternetRadio radioDisabled = new InternetRadio("name3", "streamUrl3", "homePageUrl3", false,
                Instant.now().truncatedTo(ChronoUnit.MICROS));

        internetRadioRepository.save(radio);
        internetRadioRepository.save(radioDisabled);

        // when
        Optional<InternetRadio> actual = internetRadioRepository.findByIdAndEnabledTrue(radio.getId());
        Optional<InternetRadio> actualDisabled = internetRadioRepository.findByIdAndEnabledTrue(radioDisabled.getId());

        // then
        assertTrue(actual.isPresent());
        assertEquals(radio, actual.get());
        assertTrue(actualDisabled.isEmpty());
    }
}