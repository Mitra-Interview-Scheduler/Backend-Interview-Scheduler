package com.nemal.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthRequestHeaderFilterTest {

    private final AuthRequestHeaderFilter filter = new AuthRequestHeaderFilter();

    @Test
    void rejectsAuthPostWithoutHeader() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/refresh"), response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void allowsAuthPostWithHeader() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void ignoresGetsAndNonAuthPaths() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/auth/csrf"), new MockHttpServletResponse(), chain);
        filter.doFilter(new MockHttpServletRequest("POST", "/api/candidates"), new MockHttpServletResponse(), chain);

        verify(chain, times(2)).doFilter(any(), any());
    }
}
