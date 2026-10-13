package org.airsonic.player.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentSecurityPolicyFilterTest {

    private final ContentSecurityPolicyFilter filter = new ContentSecurityPolicyFilter();

    private MockHttpServletRequest filter(MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index");
        filter.doFilter(request, response, new MockFilterChain());
        return request;
    }

    @Test
    void sendsPolicyWithTheNonceOfTheRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = filter(response);

        Object nonce = request.getAttribute(ContentSecurityPolicyFilter.NONCE_ATTRIBUTE);
        assertNotNull(nonce);
        String policy = response.getHeader("Content-Security-Policy");
        assertEquals(ContentSecurityPolicyFilter.policy(nonce.toString()), policy);
        assertTrue(policy.contains("script-src 'self' 'nonce-" + nonce + "'"));
        assertTrue(policy.contains("object-src 'none'"));
        assertFalse(policy.contains("unsafe-inline"));
        assertFalse(policy.contains("unsafe-eval"));
    }

    @Test
    void usesANewNonceForEveryRequest() throws Exception {
        Object first = filter(new MockHttpServletResponse()).getAttribute(ContentSecurityPolicyFilter.NONCE_ATTRIBUTE);
        Object second = filter(new MockHttpServletResponse()).getAttribute(ContentSecurityPolicyFilter.NONCE_ATTRIBUTE);

        assertNotEquals(first, second);
        // 18 random bytes, base64 encoded without padding
        assertEquals(24, first.toString().length());
    }
}
