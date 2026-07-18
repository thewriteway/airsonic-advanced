package org.airsonic.test;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

public class StreamIT {

    private static final Logger LOG = LoggerFactory.getLogger(StreamIT.class);

    @Test
    public void testStreamFlacAsMp3() throws Exception {
        testFileStreaming("dead", true);
    }

    @Test
    public void testStreamM4aAsMp3() throws Exception {
        testFileStreaming("dance", true);
    }

    @Test
    public void testStreamMp3() throws Exception {
        // mp3 is streamed as-is, so the bytes are deterministic
        testFileStreaming("piano", false);
    }

    private void testFileStreaming(String file, boolean transcoded) throws Exception {
        Scanner.uploadToDefaultMusicFolder(
                Paths.get(this.getClass().getResource("/blobs/stream/" + file + "/input").toURI()),
                "");
        Scanner.doScan();
        String mediaFileId = Scanner.getMediaFilesInMusicFolder().parallelStream()
                .filter(x -> {
                    LOG.info("media file: {}", x.getTitle());
                    return Strings.CI.contains(x.getTitle(), file);
                })
                .findAny()
                .map(x -> x.getId())
                .orElseThrow(() -> new RuntimeException("no media file id matched"));

        byte[] fromServer = Scanner.getMediaFileData(mediaFileId);
        String expectedBodyResource = String.format("/blobs/stream/" + file + "/responses/1.dat");
        byte[] expected = IOUtils.toByteArray(StreamIT.class.getResourceAsStream(expectedBodyResource));

        if (!transcoded) {
            assertThat(fromServer).containsExactly(expected);
            return;
        }

        // Transcoder output is not byte-stable across ffmpeg versions (the ID3 TSSE frame
        // embeds the encoder version, and decoder/encoder rounding shifts audio bytes), so
        // for transcoded streams verify structure instead of exact content: the stream must
        // be a complete MP3 of the expected length with real audio in it.
        assertThat(fromServer.length)
                .as("stream length should match the expected transcode length")
                .isCloseTo(expected.length, withinPercentage(1));
        byte[] audio = stripId3v2(fromServer);
        assertThat(audio.length).as("stream should start with an ID3v2 tag").isLessThan(fromServer.length);
        assertThat(audio.length).isGreaterThan(2);
        assertThat(audio[0] & 0xFF).as("audio should start with an MPEG frame sync").isEqualTo(0xFF);
        assertThat(audio[1] & 0xE0).isEqualTo(0xE0);
        long nonZero = 0;
        int sample = Math.min(audio.length, 100_000);
        for (int i = 0; i < sample; i++) {
            if (audio[i] != 0) {
                nonZero++;
            }
        }
        assertThat(nonZero)
                .as("encoded audio should be high-entropy, not silence or padding")
                .isGreaterThan(sample / 2L);
    }

    private static byte[] stripId3v2(byte[] data) {
        if (data.length > 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
            int size = ((data[6] & 0x7f) << 21) | ((data[7] & 0x7f) << 14) | ((data[8] & 0x7f) << 7) | (data[9] & 0x7f);
            int offset = 10 + size;
            if (offset < data.length) {
                return Arrays.copyOfRange(data, offset, data.length);
            }
        }
        return data;
    }
}
