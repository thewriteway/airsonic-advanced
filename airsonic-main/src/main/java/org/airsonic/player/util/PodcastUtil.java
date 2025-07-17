package org.airsonic.player.util;

import org.apache.commons.lang3.Strings;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class PodcastUtil {

    private PodcastUtil() {}

    /**
     * Sanitize URL.
     *
     * @param url URL to sanitize
     * @param force force sanitization
     * @return sanitized URL
     */
    public static String sanitizeUrl(String url, boolean force) {
        if (url != null && (!Strings.CS.contains(url, "://") || force)) {
            return URLDecoder.decode(url, StandardCharsets.UTF_8);
        }
        return url;
    }


    /**
     * Get error message from exception.
     * @param x exception
     * @return error message
     */
    public static String getErrorMessage(Exception x) {
        return x.getMessage() != null ? x.getMessage() : x.toString();
    }


}
