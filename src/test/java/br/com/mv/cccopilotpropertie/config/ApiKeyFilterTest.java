package br.com.mv.cccopilotpropertie.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldAllowRequestWithValidApiKey() throws ServletException, IOException {
        MockEnvironment env = new MockEnvironment();
        ApiKeyFilter filter = new ApiKeyFilter("secret-key", env);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        request.addHeader("X-API-Key", "secret-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldRejectRequestWithInvalidApiKey() throws ServletException, IOException {
        MockEnvironment env = new MockEnvironment();
        ApiKeyFilter filter = new ApiKeyFilter("secret-key", env);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        request.addHeader("X-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Informe uma X-API-Key válida"));
    }

    @Test
    void shouldAllowPreflightOptionsRequestWithoutApiKey() throws ServletException, IOException {
        MockEnvironment env = new MockEnvironment();
        ApiKeyFilter filter = new ApiKeyFilter("secret-key", env);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowPublicPathWithoutApiKey() throws ServletException, IOException {
        MockEnvironment env = new MockEnvironment();
        ApiKeyFilter filter = new ApiKeyFilter("secret-key", env);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldRejectWhenApiKeyIsBlankInProduction() throws ServletException, IOException {
        MockEnvironment env = new MockEnvironment();
        ApiKeyFilter filter = new ApiKeyFilter("", env);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("API_KEY não definida"));
    }
}
