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
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Transcoding;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test of {@link TranscodingDao}.
 *
 * @author Sindre Mehus
 */
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
@Transactional
public class TranscodingRepositoryTest {

    @Autowired
    TranscodingRepository transcodingRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlayerRepository playerRepository;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void init() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void setUp() {
        jdbcTemplate.execute("delete from transcoding");
    }

    @Test
    public void testCreateTranscoding() {
        Transcoding transcoding = new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false);
        transcodingRepository.save(transcoding);

        Transcoding newTranscoding = transcodingRepository.findAll().get(0);
        assertTranscodingEquals(transcoding, newTranscoding);
    }

    @Test
    public void testUpdateTranscoding() {
        Transcoding transcoding = new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false);
        transcodingRepository.save(transcoding);
        transcoding = transcodingRepository.findAll().get(0);

        transcoding.setName("newName");
        transcoding.setSourceFormats("newSourceFormats");
        transcoding.setTargetFormat("newTargetFormats");
        transcoding.setStep1("newStep1");
        transcoding.setStep2("newStep2");
        transcoding.setStep3("newStep3");
        transcoding.setDefaultActive(true);
        transcodingRepository.save(transcoding);

        Transcoding newTranscoding = transcodingRepository.findAll().get(0);
        assertTranscodingEquals(transcoding, newTranscoding);
    }

    @Test
    public void testDeleteTranscoding() {
        assertEquals(0, transcodingRepository.count());

        transcodingRepository.save(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", true));
        assertEquals(1, transcodingRepository.count());

        transcodingRepository.save(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", true));
        assertEquals(2, transcodingRepository.count());

        transcodingRepository.deleteById(transcodingRepository.findAll().get(0).getId());
        assertEquals(1, transcodingRepository.count());

        transcodingRepository.deleteById(transcodingRepository.findAll().get(0).getId());
        assertEquals(0, transcodingRepository.count());
    }

    @Test
    public void testPlayerTranscoding() {
        Player player = new Player();
        playerRepository.save(player);

        transcodingRepository.save(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false));
        transcodingRepository.save(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false));
        transcodingRepository.save(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false));
        Transcoding transcodingA = transcodingRepository.findAll().get(0);
        Transcoding transcodingB = transcodingRepository.findAll().get(1);
        Transcoding transcodingC = transcodingRepository.findAll().get(2);

        List<Transcoding> activeTranscodings = transcodingRepository.findByPlayersContaining(player);
        assertEquals(0, activeTranscodings.size());

        player.setTranscodings(new ArrayList<>(List.of(transcodingA)));
        playerRepository.save(player);

        activeTranscodings = transcodingRepository.findByPlayersContaining(player);
        assertEquals(1, activeTranscodings.size());
        assertTranscodingEquals(transcodingA, activeTranscodings.get(0));

        player.setTranscodings(new ArrayList<>(List.of(transcodingB, transcodingC)));
        playerRepository.save(player);

        activeTranscodings = transcodingRepository.findByPlayersContaining(player);
        assertEquals(2, activeTranscodings.size());
        assertTranscodingEquals(transcodingB, activeTranscodings.get(0));
        assertTranscodingEquals(transcodingC, activeTranscodings.get(1));

        player.setTranscodings(new ArrayList<>());
        playerRepository.save(player);
        activeTranscodings = transcodingRepository.findByPlayersContaining(player);
        assertEquals(0, activeTranscodings.size());
    }

    @Test
    public void testCascadingDeletePlayer() {
        Player player = new Player();
        playerRepository.save(player);

        transcodingRepository.save(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", true));
        Transcoding transcoding = transcodingRepository.findAll().get(0);

        player.setTranscodings(new ArrayList<>(List.of(transcoding)));
        playerRepository.save(player);
        List<Transcoding> activeTranscodings = transcodingRepository.findByPlayersContaining(player);
        assertEquals(1, activeTranscodings.size());

        playerRepository.delete(player);
        activeTranscodings = transcodingRepository.findByPlayersContaining(player);
        assertEquals(0, activeTranscodings.size());
    }

    private void assertTranscodingEquals(Transcoding expected, Transcoding actual) {
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getSourceFormats(), actual.getSourceFormats());
        assertEquals(expected.getTargetFormat(), actual.getTargetFormat());
        assertEquals(expected.getStep1(), actual.getStep1());
        assertEquals(expected.getStep2(), actual.getStep2());
        assertEquals(expected.getStep3(), actual.getStep3());
        assertEquals(expected.isDefaultActive(), actual.isDefaultActive());
    }
}
