package org.airsonic.player.spring;

import org.springframework.core.io.InputStreamResource;

import java.io.InputStream;

public class KnownLengthInputStreamResource extends InputStreamResource {
    private final long len;

    public KnownLengthInputStreamResource(InputStream inputStream, long len) {
        super(inputStream);
        this.len = len;
    }

    @Override
    public long contentLength() {
        return len;
    }
}