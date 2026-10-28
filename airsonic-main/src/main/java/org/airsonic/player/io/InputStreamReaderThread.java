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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Utility class which reads everything from an input stream and optionally logs it.
 * <p>
 * Runs on a virtual thread: the work is pure blocking I/O, so it doesn't need a platform thread
 * per transcoder process.
 *
 * @see TranscodeInputStream
 * @author Sindre Mehus
 */
public class InputStreamReaderThread implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(InputStreamReaderThread.class);

    private final InputStream input;
    private final String name;
    private final boolean log;

    public InputStreamReaderThread(InputStream input, String name, boolean log) {
        this.input = input;
        this.name = name;
        this.log = log;
    }

    /**
     * Starts draining the stream on a new virtual thread.
     *
     * @return The started thread.
     */
    public Thread start() {
        return Thread.ofVirtual().name(name + " InputStreamLogger").start(this);
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (log) {
                    LOG.info("({}) {}", name, line);
                }
            }
        } catch (IOException ignored) {
            // Stream closed or process ended; nothing more to read.
        }
    }
}
