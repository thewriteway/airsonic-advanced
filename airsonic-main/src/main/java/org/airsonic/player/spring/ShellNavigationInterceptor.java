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
package org.airsonic.player.spring;

import org.airsonic.player.controller.ShellPages;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Serves the application shell when the browser navigates directly to a main pane page, for
 * instance after a reload or when following a deep link. The shell then loads the page into its
 * main region. Requests made by the shell itself (fetch) are not top level navigations; they carry
 * the {@value #SHELL_HEADER} header and are rendered as fragments, without the parts of the page the
 * shell already has (see {@link #FRAGMENT_ATTRIBUTE}).
 */
public class ShellNavigationInterceptor implements HandlerInterceptor {

    /** Request header the shell sends with the name of the region it loads a page into. */
    public static final String SHELL_HEADER = "X-Airsonic-Shell";

    /** Request attribute (template variable) telling the templates to leave out the common head. */
    public static final String FRAGMENT_ATTRIBUTE = "shellFragment";

    private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        if (request.getHeader(SHELL_HEADER) != null) {
            request.setAttribute(FRAGMENT_ATTRIBUTE, Boolean.TRUE);
            response.addHeader("Vary", SHELL_HEADER);
        }
        if (!"GET".equals(request.getMethod())) {
            return true;
        }
        String path = URL_PATH_HELPER.getPathWithinApplication(request);
        if (!ShellPages.isPage(path)) {
            return true;
        }
        // The same URL answers with the shell or the bare page depending on who is asking.
        response.addHeader("Vary", "Sec-Fetch-Dest, " + SHELL_HEADER);
        if (!"document".equals(request.getHeader("Sec-Fetch-Dest"))
                || !"navigate".equals(request.getHeader("Sec-Fetch-Mode"))) {
            return true;
        }
        String query = request.getQueryString();
        request.setAttribute(ShellPages.MAIN_ATTRIBUTE, query == null ? path : path + "?" + query);
        request.getRequestDispatcher("/index").forward(request, response);
        return false;
    }
}
