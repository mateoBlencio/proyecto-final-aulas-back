package ar.edu.utn.frc.siga.common.web;

import ar.edu.utn.frc.siga.common.config.CorsProperties;
import ar.edu.utn.frc.siga.common.config.GeneralRateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final GeneralRateLimitProperties properties;
    private final CorsProperties corsProperties;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String origin = request.getHeader("Origin");
        boolean preflight = HttpMethod.OPTIONS.matches(request.getMethod());

        if (origin != null && corsProperties.getAllowedOrigins().contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            if (preflight) {
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "*");
            }
        }

        if (preflight) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(properties.getCapacity())
                .refillIntervally(properties.getCapacity(), Duration.ofSeconds(properties.getRefillPeriodSeconds()))
                .build();
        Bucket bucket = RateLimiterSupport.resolveBucket(ip, bandwidth, buckets);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        ProblemDetailResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests", "Rate limit exceeded, retry after " + retryAfterSeconds + " seconds");
    }
}
