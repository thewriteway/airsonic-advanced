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
 */
package org.airsonic.player.controller;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The predicted zip size is sent as Content-Length, so it must match what {@link ZipOutputStream} writes.
 */
public class DownloadControllerZipSizeTest {

    @Test
    public void zipSizeMatchesWrittenZipForAsciiNames() throws IOException {
        assertPredictionMatches(List.<Entry<String, Long>>of(Pair.of("Plain/", 0L), Pair.of("Plain/01.mp3", 1000L)));
    }

    @Test
    public void zipSizeMatchesWrittenZipForNonAsciiNames() throws IOException {
        assertPredictionMatches(List.<Entry<String, Long>>of(
                Pair.of("Café Zimmermann/", 0L),
                Pair.of("Café Zimmermann/01 Aria.flac", 1000L),
                Pair.of("坂本龍一/02 戦場のメリークリスマス.mp3", 500L)));
    }

    @Test
    public void zipSizeIsNotExactOnceZip64IsNeeded() {
        assertThat(DownloadController.isZipSizeExact(1000L, 10)).isTrue();
        assertThat(DownloadController.isZipSizeExact(0xFFFFFFFFL, 10)).isFalse();
        assertThat(DownloadController.isZipSizeExact(1000L, 0xFFFF)).isFalse();
    }

    private static void assertPredictionMatches(List<Entry<String, Long>> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            // same settings as DownloadController
            zip.setMethod(ZipOutputStream.STORED);
            for (Entry<String, Long> e : entries) {
                byte[] data = new byte[e.getValue().intValue()];
                CRC32 crc = new CRC32();
                crc.update(data);
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setSize(data.length);
                entry.setCompressedSize(data.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(data);
                zip.closeEntry();
            }
        }

        assertThat(DownloadController.zipSize(entries.stream())).isEqualTo(bytes.size());
    }
}
