package org.airsonic.player.parser.lyrics;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrcParserTest {

    private final LrcParser parser = new LrcParser();

    private Path getResourcePath(String resourceName) {
        URL url = getClass().getClassLoader().getResource("MEDIAS/lyrics/" + resourceName);
        assertNotNull(url);
        return Path.of(url.getPath());
    }

    @Test
    void parseSimpleLrcShouldSuccess() {
        Path path = getResourcePath("simple.lrc");
        List<LyricsLine> lines = parser.parse(path);
        assertEquals(12, lines.size());
        assertEquals("Lyrics1 Line 1", lines.get(0).getText());
        assertEquals("Lyrics2 Line 2", lines.get(1).getText());
        assertEquals("Lyrics5", lines.get(5).getText());
        assertEquals("Lyrics5", lines.get(6).getText());
        assertEquals("Lyrics10", lines.get(11).getText());
        assertEquals(10_370, lines.get(0).getTime());
    }

    @Test
    void parseTagLrcShouldSuccess() {
        Path path = getResourcePath("tag.lrc");
        List<LyricsLine> lines = parser.parse(path);
        assertEquals(12, lines.size());
        assertEquals("Lyrics1 Line 1", lines.get(0).getText());
        assertEquals("Lyrics2 Line 2", lines.get(1).getText());
        assertEquals("Lyrics5", lines.get(5).getText());
        assertEquals("Lyrics5", lines.get(6).getText());
        assertEquals("Lyrics10", lines.get(11).getText());
        assertEquals(10_370, lines.get(0).getTime());
    }

    @Test
    void parseA2ExtendedLrcShouldSuccess() {
        Path path = getResourcePath("a2extended.lrc");
        List<LyricsLine> lines = parser.parse(path);
        assertEquals(12, lines.size());
        assertEquals("Lyrics1 Line 1", lines.get(0).getText());
        assertEquals("Lyrics2 Line 2", lines.get(1).getText());
        assertEquals("Lyrics5", lines.get(5).getText());
        assertEquals("Lyrics5", lines.get(6).getText());
        assertEquals("Lyrics10", lines.get(11).getText());
        assertEquals(10_370, lines.get(0).getTime());
    }

    @Test
    void parseWalaokeLrcShouldSuccess() {
        Path path = getResourcePath("walaoke.lrc");
        List<LyricsLine> lines = parser.parse(path);
        assertEquals(12, lines.size());
        assertEquals("F: Lyrics3", lines.get(3).getText());
        assertEquals("M: Lyrics4", lines.get(4).getText());
        assertEquals("Lyrics5", lines.get(5).getText());
        assertEquals("Lyrics5", lines.get(6).getText());
        assertTrue(lines.get(9).getText().contains("D: Lyrics8"));
        assertEquals(10_370, lines.get(0).getTime());
    }

    @Test
    void parseInvalidLrcShouldReturnEmptyList() {
        // invalid.lrc does not exist, so we expect an empty list
        Path path = getResourcePath("invalid.lrc");
        List<LyricsLine> lines = parser.parse(path);
        assertTrue(lines.isEmpty());
    }

    @Test
    void parseWithBlankPathShouldReturnEmptyList() {
        List<LyricsLine> lines = parser.parse("");
        assertTrue(lines.isEmpty());
    }

    @Test
    void parseWithNullPathShouldReturnEmptyList() {
        List<LyricsLine> lines = parser.parse((Path) null);
        assertTrue(lines.isEmpty());
    }
}