package com.nemal.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    /** Test tokens look like "valid:<email>"; anything else is treated as invalid. */
    private static final Function<String, String> TEST_RESOLVER =
            token -> token.startsWith("valid:") ? token.substring("valid:".length()) : null;

    private static RateLimitFilter filter(int auth, int google, int session, int writes, boolean trustForwardedFor) {
        return new RateLimitFilter(auth, google, session, writes, trustForwardedFor, TEST_RESOLVER);
    }

    @Test
    void authEndpointsAreLimitedPerIp() throws Exception {
        RateLimitFilter filter = filter(2, 60, 120, 120, false);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(authRequest(), new MockHttpServletResponse(), chain);
        filter.doFilter(authRequest(), new MockHttpServletResponse(), chain);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(authRequest(), blocked, chain);

        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("Too many requests"));
        assertTrue(blocked.getContentAsString().contains("retryAfterSeconds"));
        assertTrue(Integer.parseInt(blocked.getHeader("Retry-After")) > 0);
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void candidateCreateIsCountedAsSensitiveWrite() throws Exception {
        RateLimitFilter filter = filter(20, 60, 120, 1, false);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/candidates");
        first.setRemoteAddr("10.0.0.8");
        filter.doFilter(first, new MockHttpServletResponse(), chain);

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/candidates");
        second.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(second, blocked, chain);

        assertEquals(429, blocked.getStatus());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void writesAreCountedPerSignedInUserSoColleaguesOnOneIpDoNotShare() throws Exception {
        RateLimitFilter filter = filter(20, 60, 120, 1, false);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(write("valid:hr1@mitrai.com"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse colleague = new MockHttpServletResponse();
        filter.doFilter(write("valid:hr2@mitrai.com"), colleague, chain);
        MockHttpServletResponse sameUserAgain = new MockHttpServletResponse();
        filter.doFilter(write("valid:hr1@mitrai.com"), sameUserAgain, chain);

        assertEquals(200, colleague.getStatus());
        assertEquals(429, sameUserAgain.getStatus());
    }

    @Test
    void invalidBearerTokenFallsBackToIpBucket() throws Exception {
        RateLimitFilter filter = filter(20, 60, 120, 1, false);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(write("forged-1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse secondForged = new MockHttpServletResponse();
        filter.doFilter(write("forged-2"), secondForged, chain);

        assertEquals(429, secondForged.getStatus());
    }

    @Test
    void googleSignInHasItsOwnHigherBucket() throws Exception {
        RateLimitFilter filter = filter(1, 3, 120, 120, false);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(authRequest(), new MockHttpServletResponse(), chain);
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse google = new MockHttpServletResponse();
            filter.doFilter(post("/api/auth/google"), google, chain);
            assertEquals(200, google.getStatus());
        }
        MockHttpServletResponse fourth = new MockHttpServletResponse();
        filter.doFilter(post("/api/auth/google"), fourth, chain);
        assertEquals(429, fourth.getStatus());
    }

    @Test
    void csrfTokenEndpointIsNotLimited() throws Exception {
        RateLimitFilter filter = filter(1, 1, 1, 120, false);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/csrf");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
        }
        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void refreshUsesItsOwnBucketSoItDoesNotLockOutLogin() throws Exception {
        RateLimitFilter filter = filter(1, 60, 1, 120, false);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(post("/api/auth/refresh"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse login = new MockHttpServletResponse();
        filter.doFilter(authRequest(), login, chain);

        assertEquals(200, login.getStatus());
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void behindProxyEachClientGetsItsOwnBucketFromRightMostForwardedIp() throws Exception {
        RateLimitFilter filter = filter(1, 60, 120, 120, true);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(proxiedLogin("203.0.113.10"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse otherClient = new MockHttpServletResponse();
        filter.doFilter(proxiedLogin("198.51.100.20"), otherClient, chain);

        assertEquals(200, otherClient.getStatus());
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void spoofedLeftMostForwardedIpDoesNotBypassTheLimit() throws Exception {
        RateLimitFilter filter = filter(1, 60, 120, 120, true);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(proxiedLogin("1.1.1.1, 203.0.113.10"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse spoofed = new MockHttpServletResponse();
        filter.doFilter(proxiedLogin("2.2.2.2, 203.0.113.10"), spoofed, chain);

        assertEquals(429, spoofed.getStatus());
    }

    private MockHttpServletRequest write(String bearerToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/candidates/5");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("Authorization", "Bearer " + bearerToken);
        return request;
    }

    private MockHttpServletRequest proxiedLogin(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    private MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private MockHttpServletRequest authRequest() {
        return post("/api/auth/login");
    }
}
