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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.filter;

import org.airsonic.player.controller.JAXBWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.subsonic.restapi.Error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Logs one line per Subsonic REST API call, so client problems can be diagnosed from the server log.
 * <p>
 * Calls that fail (a Subsonic error response, an HTTP status of 400 or above, or an exception) are
 * logged at INFO; successful calls only at DEBUG. To see every call, set
 * {@code logging.level.org.airsonic.player.filter.RESTRequestLoggingFilter=DEBUG}.
 * <p>
 * The filter is registered after Spring Security, so it only logs calls that passed authentication.
 * Credentials ({@code p}, {@code t}, {@code s}, {@code password}, {@code apiKey}, {@code jwt}) are
 * never written to the log.
 */
public class RESTRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RESTRequestLoggingFilter.class);

    private static final Set<String> SENSITIVE_PARAMS = Set.of("p", "t", "s", "password", "apikey", "jwt");
    /** Parameters already shown in the fixed part of the log line. */
    private static final Set<String> HEADER_PARAMS = Set.of("u", "c", "v", "f");
    private static final int MAX_VALUE_LENGTH = 200;
    private static final int MAX_VALUES_PER_PARAM = 10;
    private static final String REDACTED = "***";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!LOG.isInfoEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.nanoTime();
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            boolean failed = failure != null
                    || response.getStatus() >= 400
                    || request.getAttribute(JAXBWriter.ERROR_REQUEST_ATTRIBUTE) != null;
            if (failed) {
                LOG.info("{}", describe(request, response, failure, elapsedMs));
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("{}", describe(request, response, null, elapsedMs));
            }
        }
    }

    /**
     * Builds the log line, e.g.
     * {@code REST GET /rest/getArtists.view from 10.0.0.5 client=play:Sub v=1.16.1 f=json user=bob
     * params={musicFolderId=1} -> HTTP 200, Subsonic error 70 "Album not found." in 12 ms}.
     */
    static String describe(HttpServletRequest request, HttpServletResponse response, Throwable failure, long elapsedMs) {
        StringBuilder sb = new StringBuilder("REST ")
                .append(request.getMethod()).append(' ')
                .append(request.getRequestURI())
                .append(" from ").append(request.getRemoteAddr())
                .append(" client=").append(param(request, "c"))
                .append(" v=").append(param(request, "v"))
                .append(" f=").append(param(request, "f"))
                .append(" user=").append(param(request.getRemoteUser()));

        Map<String, String> params = otherParams(request);
        if (!params.isEmpty()) {
            sb.append(" params=").append(params);
        }

        sb.append(" -> HTTP ").append(response.getStatus());

        Object error = request.getAttribute(JAXBWriter.ERROR_REQUEST_ATTRIBUTE);
        if (error instanceof Error e) {
            sb.append(", Subsonic error ").append(e.getCode()).append(" \"").append(e.getMessage()).append('"');
        } else if (failure == null && response.getStatus() < 400) {
            sb.append(", ok");
        }

        if (failure != null) {
            sb.append(", exception ").append(failure.getClass().getName());
            if (failure.getMessage() != null) {
                sb.append(": ").append(failure.getMessage());
            }
        }

        return sb.append(" in ").append(elapsedMs).append(" ms").toString();
    }

    private static String param(HttpServletRequest request, String name) {
        return param(request.getParameter(name));
    }

    private static String param(String value) {
        return value == null ? "-" : truncate(value);
    }

    private static Map<String, String> otherParams(HttpServletRequest request) {
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String name = entry.getKey();
            if (HEADER_PARAMS.contains(name)) {
                continue;
            }
            if (SENSITIVE_PARAMS.contains(name.toLowerCase(Locale.ROOT))) {
                result.put(name, REDACTED);
                continue;
            }
            String[] values = entry.getValue();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length && i < MAX_VALUES_PER_PARAM; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(truncate(values[i]));
            }
            if (values.length > MAX_VALUES_PER_PARAM) {
                sb.append(",... (").append(values.length).append(" values)");
            }
            result.put(name, sb.toString());
        }
        return result;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String oneLine = value.replace('\r', ' ').replace('\n', ' ');
        return oneLine.length() <= MAX_VALUE_LENGTH ? oneLine : oneLine.substring(0, MAX_VALUE_LENGTH) + "...";
    }
}
