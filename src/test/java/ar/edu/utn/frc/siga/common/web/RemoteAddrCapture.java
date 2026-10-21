package ar.edu.utn.frc.siga.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/** Test filter that records the remote address the app sees, same as RateLimitFilter and login. */
@TestConfiguration
class RemoteAddrCapture {

    static final AtomicReference<String> LAST = new AtomicReference<>();

    @Bean
    OncePerRequestFilter remoteAddrCaptureFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                LAST.set(request.getRemoteAddr());
                chain.doFilter(request, response);
            }
        };
    }
}
