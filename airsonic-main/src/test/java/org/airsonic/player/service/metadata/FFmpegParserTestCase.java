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
package org.airsonic.player.service.metadata;

import org.airsonic.player.util.Util;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FFmpegParserTestCase {

    private static final String[] ALBUM_ARTIST_KEYS = {"album_artist", "ALBUMARTIST"};

    @Test
    public void testAlbumArtistFromVorbisAlbumartist() {
        // Vorbis comments (FLAC/OGG/Opus): canonical key is ALBUMARTIST, no separator.
        assertEquals("Daft Punk", FFmpegParser.getDataAny(formatTags("ALBUMARTIST", "Daft Punk"), ALBUM_ARTIST_KEYS));
    }

    @Test
    public void testAlbumArtistFromId3Mp4Normalized() {
        // ID3v2 TPE2 and MP4 aART surface as album_artist after ffprobe normalization.
        assertEquals("Daft Punk", FFmpegParser.getDataAny(formatTags("album_artist", "Daft Punk"), ALBUM_ARTIST_KEYS));
    }

    @Test
    public void testAlbumArtistFromStreamTags() {
        JsonNode node = Util.getObjectMapper().readTree("{\"streams\":[{\"tags\":{\"ALBUMARTIST\":\"Daft Punk\"}}]}");
        assertEquals("Daft Punk", FFmpegParser.getDataAny(node, ALBUM_ARTIST_KEYS));
    }

    @Test
    public void testFirstKeyWins() {
        JsonNode node = Util.getObjectMapper().readTree(
                "{\"format\":{\"tags\":{\"album_artist\":\"First\",\"ALBUMARTIST\":\"Second\"}}}");
        assertEquals("First", FFmpegParser.getDataAny(node, ALBUM_ARTIST_KEYS));
    }

    @Test
    public void testAlbumArtistAbsentReturnsNull() {
        assertNull(FFmpegParser.getDataAny(formatTags("artist", "Daft Punk"), ALBUM_ARTIST_KEYS));
    }

    private static JsonNode formatTags(String key, String value) {
        return Util.getObjectMapper().createObjectNode()
                .set("format", Util.getObjectMapper().createObjectNode()
                        .set("tags", Util.getObjectMapper().createObjectNode().put(key, value)));
    }
}
