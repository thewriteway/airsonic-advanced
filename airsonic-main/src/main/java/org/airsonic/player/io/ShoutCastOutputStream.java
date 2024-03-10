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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Implements SHOUTcast support by decorating an existing output stream.
 * <p/>
 * Based on protocol description found on
 * <em>http://www.smackfu.com/stuff/programming/shoutcast.html</em>
 *
 * @author Sindre Mehus
 */
public class ShoutCastOutputStream extends OutputStream {

    /**
     * Number of bytes between each SHOUTcast metadata block.
     */
    public static final int META_DATA_INTERVAL = 20480;

    /**
     * The underlying output stream to decorate.
     */
    private final OutputStream out;

    /**
     * Keeps track of the number of bytes written (excluding meta-data).  Between 0 and {@link #META_DATA_INTERVAL}.
     */
    private int byteCount;

    /**
     * The last stream title sent.
     */
    private String previousStreamTitle;

    private final Supplier<String> titleSupplier;

    /**
     * Creates a new SHOUTcast-decorated stream for the given output stream.
     *
     * @param out           The output stream to decorate.
     * @param titleSupplier Meta-data title is fetched from this supplier.
     */
    public ShoutCastOutputStream(OutputStream out, Supplier<String> titleSupplier) {
        this.out = out;
        this.titleSupplier = titleSupplier;
    }

    /**
     * Writes the given byte array to the underlying stream, adding SHOUTcast meta-data as necessary.
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {

        int bytesWritten = 0;
        while (bytesWritten < len) {

            // 'n' is the number of bytes to write before the next potential meta-data block.
            int n = Math.min(len - bytesWritten, ShoutCastOutputStream.META_DATA_INTERVAL - byteCount);

            out.write(b, off + bytesWritten, n);
            bytesWritten += n;
            byteCount += n;

            // Reached meta-data block?
            if (byteCount % ShoutCastOutputStream.META_DATA_INTERVAL == 0) {
                writeMetaData();
                byteCount = 0;
            }
        }
    }

    /**
     * Writes the given byte array to the underlying stream, adding SHOUTcast meta-data as necessary.
     */
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Writes the given byte to the underlying stream, adding SHOUTcast meta-data as necessary.
     */
    @Override
    public void write(int b) throws IOException {
        byte[] buf = new byte[]{(byte) b};
        write(buf);
    }

    /**
     * Flushes the underlying stream.
     */
    @Override
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Closes the underlying stream.
     */
    @Override
    public void close() throws IOException {
        out.close();
    }

    private void writeMetaData() throws IOException {
        String streamTitle = titleSupplier.get();
//                StringUtils.trimToEmpty(settingsService.getWelcomeTitle());

//        MediaFile result;
//        synchronized (playQueue) {
//            result = playQueue.getCurrentFile();
//        }
//        MediaFile mediaFile = result;
//        if (mediaFile != null) {
//            streamTitle = mediaFile.getArtist() + " - " + mediaFile.getTitle();
//        }

        byte[] bytes;

        if (streamTitle.equals(previousStreamTitle)) {
            bytes = new byte[0];
        } else {
            previousStreamTitle = streamTitle;
            bytes = createStreamTitle(streamTitle);
        }

        // Length in groups of 16 bytes.
        int length = bytes.length / 16;
        if (bytes.length % 16 > 0) {
            length++;
        }

        // Write the length as a single byte.
        out.write(length);

        // Write the message.
        out.write(bytes);

        // Write padding zero bytes.
        int padding = length * 16 - bytes.length;
        for (int i = 0; i < padding; i++) {
            out.write(0);
        }
    }

    /**
     * creates a stream title for the given title data.
     *
     * @param title title data
     * @return stream title.
     */
    private byte[] createStreamTitle(String title) {
        // Remove any quotes from the title.
        title = title.replaceAll("'", "");

        title = "StreamTitle='" + title + "';";

        // Original icy specification needs ascii encode,
        // but external player (Winamp/AIMP/foobar etc) support UTF-8 encoded value.
        return title.getBytes(StandardCharsets.UTF_8);
    }
}
