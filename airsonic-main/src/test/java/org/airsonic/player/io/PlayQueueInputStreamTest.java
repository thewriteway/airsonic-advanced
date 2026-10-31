package org.airsonic.player.io;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlayQueueInputStreamTest {

    @Mock
    private PlayQueue queue;

    @Mock
    private MediaFile file;

    private final List<Long> streamedBytes = new ArrayList<>();

    private PlayQueueInputStream stream;

    @BeforeEach
    public void setUp() {
        when(queue.getCurrentFile()).thenReturn(file);
        when(queue.getStatus()).thenReturn(PlayQueue.Status.PLAYING);
        stream = new PlayQueueInputStream(queue, f -> {}, (bytes, f) -> streamedBytes.add(bytes),
                f -> new ByteArrayInputStream(new byte[1000]));
    }

    @Test
    public void reportsBytesStreamedNotReadCalls() throws IOException {
        byte[] buffer = new byte[100];
        for (int i = 0; i < 5; i++) {
            stream.read(buffer);
        }
        stream.read();

        stream.close();

        assertThat(streamedBytes).containsExactly(501L);
    }

    @Test
    public void skippedBytesAreNotCounted() throws IOException {
        assertThat(stream.skip(900)).isEqualTo(900);
        stream.read(new byte[50]);

        stream.close();

        assertThat(streamedBytes).containsExactly(50L);
    }
}
