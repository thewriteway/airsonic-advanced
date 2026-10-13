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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Sends a Content-Security-Policy that only lets the application's own scripts run: script files
 * served by Airsonic and inline scripts carrying the nonce of the request. Inline event handler
 * attributes and javascript: URLs are refused by the browser, which defeats most injected markup.
 * <p>
 * The nonce is exposed as request attribute {@link #NONCE_ATTRIBUTE}; the templates add it to every
 * script element (see {@link org.airsonic.player.spring.CspNonceDialect}).
 */
public class ContentSecurityPolicyFilter implements Filter {

    public static final String NONCE_ATTRIBUTE = "cspNonce";

    private static final String HEADER = "Content-Security-Policy";

    private final SecureRandom random = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String nonce = newNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);
        ((HttpServletResponse) response).setHeader(HEADER, policy(nonce));
        chain.doFilter(request, response);
    }

    /**
     * @param nonce the nonce of the request
     * @return the policy: scripts from this server, inline scripts with the nonce, the Chromecast
     *         sender library and reCAPTCHA (www.gstatic.com, www.google.com/recaptcha/); workers may
     *         also be created from blobs (hls.js does that)
     */
    static String policy(String nonce) {
        return "script-src 'self' 'nonce-" + nonce + "' https://www.gstatic.com https://www.google.com/recaptcha/; "
                + "worker-src 'self' blob:; "
                + "object-src 'none'; "
                + "base-uri 'self'";
    }

    private String newNonce() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
