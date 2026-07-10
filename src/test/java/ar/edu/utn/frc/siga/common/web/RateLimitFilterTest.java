package ar.edu.utn.frc.siga.common.web;

import ar.edu.utn.frc.siga.common.config.CorsProperties;
import ar.edu.utn.frc.siga.common.config.GeneralRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private static final String ORIGIN = "http://localhost:5173";

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        GeneralRateLimitProperties properties = new GeneralRateLimitProperties();
        properties.setCapacity(2);
        properties.setRefillPeriodSeconds(60);

        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of(ORIGIN));

        filter = new RateLimitFilter(properties, corsProperties);
    }

    private HttpServletRequest request(String method, String ip) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRemoteAddr()).thenReturn(ip);
        when(request.getHeader("Origin")).thenReturn(ORIGIN);
        return request;
    }

    private HttpServletResponse responseMock() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));
        return response;
    }

    @Test
    void shouldAllowRequests_withinCapacity() throws Exception {
        HttpServletResponse response = responseMock();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("GET", "10.0.0.1"), response, chain);
        filter.doFilter(request("GET", "10.0.0.1"), response, chain);

        verify(chain, org.mockito.Mockito.times(2)).doFilter(anyRequest(), anyResponse());
        verify(response, never()).setStatus(429);
    }

    @Test
    void shouldReturn429WithCorsHeaders_whenCapacityExceeded() throws Exception {
        HttpServletResponse response = responseMock();
        FilterChain chain = mock(FilterChain.class);
        String ip = "10.0.0.2";

        filter.doFilter(request("GET", ip), response, chain);
        filter.doFilter(request("GET", ip), response, chain);
        filter.doFilter(request("GET", ip), response, chain);

        verify(response).setStatus(429);
        verify(response, org.mockito.Mockito.atLeastOnce()).setHeader("Access-Control-Allow-Origin", ORIGIN);
        verify(response, org.mockito.Mockito.atLeastOnce()).setHeader(anyString(), anyString());
    }

    @Test
    void preflightOptions_shouldNeverCountAgainstTheLimit() throws Exception {
        HttpServletResponse response = responseMock();
        FilterChain chain = mock(FilterChain.class);
        String ip = "10.0.0.3";

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request("OPTIONS", ip), response, chain);
        }

        verify(chain, org.mockito.Mockito.times(10)).doFilter(anyRequest(), anyResponse());
        verify(response, never()).setStatus(429);
    }

    private HttpServletRequest anyRequest() {
        return org.mockito.ArgumentMatchers.any(HttpServletRequest.class);
    }

    private HttpServletResponse anyResponse() {
        return org.mockito.ArgumentMatchers.any(HttpServletResponse.class);
    }
}
