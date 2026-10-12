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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.service;

import org.airsonic.player.service.StatusReport.LogEntry;
import org.airsonic.player.service.StatusReport.LogInfo;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the latest warnings and errors in the Airsonic log for the status page.
 */
@Service
public class LogSummaryService {

    private static final Logger LOG = LoggerFactory.getLogger(LogSummaryService.class);

    /** Number of log lines searched for warnings and errors, from the end of the log. */
    private static final int LINES_SCANNED = 2000;
    /** Number of warnings and errors reported. */
    private static final int ENTRIES = 15;
    /**
     * A line of logging.pattern.file: "2026-10-12 10:57:27.226  WARN --- logger : message". Logger names have
     * no spaces, so the separator after them is unambiguous.
     */
    private static final Pattern LOG_LINE = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) +(WARN|ERROR) +--- +(\\S+) +: (.*)$");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final SettingsService settingsService;

    public LogSummaryService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public LogInfo getLog() {
        String file = settingsService.getLogFile();
        Path path = file == null ? null : Paths.get(file);
        if (path == null || !Files.isRegularFile(path)) {
            return new LogInfo(file, false, 0, 0, List.of());
        }
        List<String> lines = new ArrayList<>();
        try (ReversedLinesFileReader reader = ReversedLinesFileReader.builder().setFile(path.toFile())
                .setCharset(StandardCharsets.UTF_8).get()) {
            String line;
            while (lines.size() < LINES_SCANNED && (line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            LOG.debug("Could not read the log file {}", path, e);
            return new LogInfo(file, false, 0, 0, List.of());
        }
        // newest first
        List<LogEntry> entries = parseLogEntries(lines);
        int warnings = (int) entries.stream().filter(e -> "WARN".equals(e.level())).count();
        return new LogInfo(file, true, warnings, entries.size() - warnings,
                entries.subList(0, Math.min(ENTRIES, entries.size())));
    }

    /**
     * Picks the warnings and errors out of log lines (in the order given). Continuation lines, e.g. stack traces,
     * are skipped.
     */
    static List<LogEntry> parseLogEntries(List<String> lines) {
        List<LogEntry> entries = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = LOG_LINE.matcher(line);
            if (matcher.matches()) {
                Long time;
                try {
                    time = LocalDateTime.parse(matcher.group(1), LOG_TIME).atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli();
                } catch (DateTimeParseException e) {
                    time = null;
                }
                entries.add(new LogEntry(time, matcher.group(2), matcher.group(3), matcher.group(4)));
            }
        }
        return entries;
    }
}
