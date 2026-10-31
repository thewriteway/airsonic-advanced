package org.airsonic.player.io;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class PlayQueueInputStream extends InputStream {
    private final PlayQueue queue;
    private final Consumer<MediaFile> fileStartListener;
    private final BiConsumer<Long, MediaFile> fileEndListener;
    private final Function<MediaFile, InputStream> streamGenerator;
    private InputStream currentStream;
    private MediaFile currentFile;
    private long bytesRead = 0;
    private boolean skipping = false;

    public PlayQueueInputStream(PlayQueue queue, Consumer<MediaFile> fileStartListener,
            BiConsumer<Long, MediaFile> fileEndListener, Function<MediaFile, InputStream> streamGenerator) {
        this.queue = queue;
        this.fileStartListener = fileStartListener;
        this.fileEndListener = fileEndListener;
        this.streamGenerator = streamGenerator;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b);
        return n == -1 ? -1 : b[0];
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        prepare();
        if (currentStream == null || queue.getStatus() == PlayQueue.Status.STOPPED) {
            return -1;
        }

        int n = currentStream.read(b, off, len);

        // If end of song reached, skip to next song and call read() again.
        if (n == -1) {
            queue.next();
            closeStream();
            return read(b, off, len);
        }

        if (!skipping) {
            bytesRead += n;
        }
        return n;
    }

    /**
     * Skipped bytes (time offsets, range requests) are not counted as streamed.
     */
    @Override
    public long skip(long n) throws IOException {
        skipping = true;
        try {
            return super.skip(n);
        } finally {
            skipping = false;
        }
    }

    private void prepare() throws IOException {
        MediaFile file = queue.getCurrentFile();
        if (file == null) {
            closeStream();
        } else if (!file.equals(currentFile)) {
            closeStream();
            currentFile = file;
            fileStartListener.accept(currentFile);
            currentStream = streamGenerator.apply(currentFile);
        }
    }

    public void closeStream() throws IOException {
        if (currentStream != null) {
            currentStream.close();
            currentStream = null;
        }
        if (currentFile != null) {
            fileEndListener.accept(bytesRead, currentFile);
            currentFile = null;
        }
        bytesRead = 0;
    }

    @Override
    public void close() throws IOException {
        closeStream();
        super.close();
    }
}
