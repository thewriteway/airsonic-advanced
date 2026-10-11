package org.airsonic.player.spring;

import org.airsonic.player.controller.ShellPages;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellNavigationInterceptorTest {

    private final ShellNavigationInterceptor interceptor = new ShellNavigationInterceptor();

    private static MockHttpServletRequest navigation(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Sec-Fetch-Dest", "document");
        request.addHeader("Sec-Fetch-Mode", "navigate");
        return request;
    }

    @Test
    void forwardsPageNavigationToShell() throws Exception {
        MockHttpServletRequest request = navigation("GET", "/main.view");
        request.setQueryString("id=3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals("/index", response.getForwardedUrl());
        assertEquals("/main.view?id=3", request.getAttribute(ShellPages.MAIN_ATTRIBUTE));
        assertEquals("Sec-Fetch-Dest", response.getHeader("Vary"));
    }

    @Test
    void passesShellRequestsThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/main.view");
        request.addHeader("Sec-Fetch-Dest", "empty");
        request.addHeader("Sec-Fetch-Mode", "cors");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertNull(response.getForwardedUrl());
        assertEquals("Sec-Fetch-Dest", response.getHeader("Vary"));
    }

    @Test
    void passesOtherNavigationsThrough() throws Exception {
        MockHttpServletRequest[] requests = {
            navigation("GET", "/download.view"),
            navigation("GET", "/index"),
            navigation("POST", "/home.view"),
            new MockHttpServletRequest("GET", "/home.view")
        };
        for (MockHttpServletRequest request : requests) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertTrue(interceptor.preHandle(request, response, new Object()), request.getRequestURI());
            assertNull(response.getForwardedUrl(), request.getRequestURI());
        }
    }
}
