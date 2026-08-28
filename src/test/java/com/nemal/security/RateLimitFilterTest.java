package com.nemal.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    @Test
    void authEndpointsAreLimitedPerIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 120, false);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(authRequest(), new MockHttpServletResponse(), chain);
        filter.doFilter(authRequest(), new MockHttpServletResponse(), chain);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(authRequest(), blocked, chain);

        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("Too many requests"));
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void candidateCreateIsCountedAsSensitiveWrite() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(20, 1, false);
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

    private MockHttpServletRequest authRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
