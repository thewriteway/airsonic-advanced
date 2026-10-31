package org.airsonic.player.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TranscodingServiceTmpFileTest {

    @TempDir
    private Path tmpDir;

    private final TranscodingService transcodingService = new TranscodingService();

    @Test
    public void deletesOnlyStaleTranscodeTmpFiles() throws IOException {
        Instant now = Instant.now();
        Path stale = createFile("airsonic-transcode-123456789.flac", now.minus(Duration.ofHours(7)));
        Path staleLegacy = createFile("airsonic987654321.wav", now.minus(Duration.ofHours(7)));
        Path fresh = createFile("airsonic-transcode-555.flac", now);
        Path otherFile = createFile("airsonic-backup.zip", now.minus(Duration.ofHours(7)));
        Path otherDir = Files.createDirectory(tmpDir.resolve("airsonic42.d"));

        int deleted = transcodingService.deleteStaleTmpFiles(tmpDir, now.minus(Duration.ofHours(6)));

        assertEquals(2, deleted);
        assertFalse(Files.exists(stale));
        assertFalse(Files.exists(staleLegacy));
        assertTrue(Files.exists(fresh));
        assertTrue(Files.exists(otherFile));
        assertTrue(Files.exists(otherDir));
    }

    private Path createFile(String name, Instant lastModified) throws IOException {
        Path file = Files.createFile(tmpDir.resolve(name));
        Files.setLastModifiedTime(file, FileTime.from(lastModified));
        return file;
    }
}
