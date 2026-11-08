package org.airsonic.player.filter;

import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.SubsonicRESTController.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RESTRequestLoggingFilterTest {

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rest/getArtists.view");
        request.setRemoteAddr("10.0.0.5");
        request.addParameter("u", "bob");
        request.addParameter("t", "secret-token");
        request.addParameter("s", "secret-salt");
        request.addParameter("c", "play:Sub");
        request.addParameter("v", "1.16.1");
        request.addParameter("f", "json");
        request.addParameter("musicFolderId", "1");
        return request;
    }

    @Test
    void describesSuccessfulCall() {
        MockHttpServletRequest request = request();
        request.setRemoteUser("bob");

        String line = RESTRequestLoggingFilter.describe(request, new MockHttpServletResponse(), null, 12);

        assertEquals("REST GET /rest/getArtists.view from 10.0.0.5 client=play:Sub v=1.16.1 f=json user=bob"
                + " params={musicFolderId=1, s=***, t=***} -> HTTP 200, ok in 12 ms", line);
    }

    @Test
    void reportsSubsonicErrorWithoutCredentials() {
        MockHttpServletRequest request = request();
        request.setRemoteUser("bob");
        request.addParameter("p", "plain-password");
        request.setAttribute(JAXBWriter.ERROR_REQUEST_ATTRIBUTE,
                new JAXBWriter().createErrorResponse(ErrorCode.NOT_FOUND, "Album not found.").getError());

        String line = RESTRequestLoggingFilter.describe(request, new MockHttpServletResponse(), null, 3);

        assertTrue(line.contains("user=bob "), line);
        assertTrue(line.endsWith("-> HTTP 200, Subsonic error 70 \"Album not found.\" in 3 ms"), line);
        assertFalse(line.contains("plain-password"), line);
        assertFalse(line.contains("secret-token"), line);
        assertFalse(line.contains("secret-salt"), line);
    }

    @Test
    void reportsHttpErrorAndException() {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        String line = RESTRequestLoggingFilter.describe(request, response, new IllegalStateException("boom"), 7);

        assertTrue(line.endsWith("-> HTTP 500, exception java.lang.IllegalStateException: boom in 7 ms"), line);
    }

    @Test
    void truncatesLongParameterLists() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rest/savePlayQueue.view");
        for (int i = 0; i < 25; i++) {
            request.addParameter("id", String.valueOf(i));
        }

        String line = RESTRequestLoggingFilter.describe(request, new MockHttpServletResponse(), null, 1);

        assertTrue(line.contains("id=0,1,2,3,4,5,6,7,8,9,... (25 values)"), line);
    }
}
