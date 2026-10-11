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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.parser.lyrics;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LrcParser {

    private static final Logger LOG = LoggerFactory.getLogger(LrcParser.class);

    // Extended format meta information lines, e.g. [ar:Artist]
    private static final Pattern META_LINE = Pattern.compile("\\[\\w+?:.*\\]");
    // A2 extension word timing directly after the line time tag
    private static final Pattern A2_AFTER_TIME_TAG = Pattern.compile("(^\\[\\d{2}:\\d{2}(\\.\\d{2,3})?\\])\\s*<.*?>");
    // Any remaining <...> word timing
    private static final Pattern A2_WORD_TAG = Pattern.compile("\\s<.*?>");

    /**
     * Parses a LRC file and returns a list of LyricsLine objects.
     *
     * @param lrcFilePath The path to the LRC file.
     * @return A list of LyricsLine objects parsed from the LRC file.
     * @throws IOException If an error occurs while reading the file.
     */
    @Nullable
    public LrcFile parse(String lrcFilePath) {
        try {
            if (StringUtils.isBlank(lrcFilePath)) {
                LOG.warn("LRC file path is blank");
                return null;
            }
            Path path = Path.of(lrcFilePath);
            return parse(path);
        } catch (InvalidPathException e) {
            LOG.error("Invalid LRC file path: {}", lrcFilePath, e);
            return null;
        }
    }

    /**
     * Parses a LRC file and returns a list of LyricsLine objects.
     *
     * @param lrcFilePath The path to the LRC file.
     * @return A list of LyricsLine objects parsed from the LRC file.
     */
    public LrcFile parse(Path lrcFilePath) {
        if (lrcFilePath == null || !Files.exists(lrcFilePath)) {
            LOG.warn("LRC file does not exist: {}", lrcFilePath);
            return null;
        }
        List<LyricsLine> lines = new ArrayList<>();
        // InputStreamReader replaces malformed bytes rather than failing like Files.newBufferedReader
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(lrcFilePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Ignore extended format meta information lines
                if (META_LINE.matcher(line).matches()) {
                    continue;
                }
                // Remove A2 Extension part (e.g., <...> after time tag)
                line = A2_AFTER_TIME_TAG.matcher(line).replaceAll("$1");
                // Also remove any <...> that may appear after the time tag
                line = A2_WORD_TAG.matcher(line).replaceAll("");

                String[] parts = line.split("]");
                for (int i = 0; i < parts.length - 1; i++) {
                    String timeTag = parts[i].replace("[", "").trim();
                    String text = parts[parts.length - 1].trim();
                    long time = parseTimeTag(timeTag);
                    lines.add(new LyricsLine(time, text));
                }
            }
            return new LrcFile(lines, Files.getLastModifiedTime(lrcFilePath).toInstant().truncatedTo(ChronoUnit.MICROS));
        } catch (IOException e) {
            LOG.warn("Error reading LRC file: {}", lrcFilePath);
            return null;
        } catch (Exception e) {
            LOG.warn("Unexpected error while parsing LRC file: {}", lrcFilePath);
            return null;
        }
    }

    private long parseTimeTag(String tag) {
        // tag format: mm:ss.xx or mm:ss
        String[] minSec = tag.split(":");
        if (minSec.length != 2) {
            LOG.warn("Invalid LRC time tag format: {}", tag);
            throw new IllegalArgumentException("Invalid LRC time tag format: " + tag);
        }
        int min = Integer.parseInt(minSec[0]);
        String[] secHundredths = minSec[1].split("\\.");
        int sec = Integer.parseInt(secHundredths[0]);
        int ms = 0;
        if (secHundredths.length > 1) {
            // xx is hundredths of a second, so multiply by 10 to get milliseconds
            int hundredths = Integer.parseInt((secHundredths[1] + "0").substring(0, 2));
            ms = hundredths * 10;
        }
        return min * 60_000 + sec * 1000 + ms;
    }

}
