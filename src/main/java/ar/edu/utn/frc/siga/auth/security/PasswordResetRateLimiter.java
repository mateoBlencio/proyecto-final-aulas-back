package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.config.PasswordResetProperties;
import ar.edu.utn.frc.siga.auth.exception.TooManyRequestsException;
import ar.edu.utn.frc.siga.common.web.RateLimiterSupport;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRateLimiter {

    private static final int MAX_CACHE_SIZE = 10_000;

    private final PasswordResetProperties properties;
    private final Cache<String, Bucket> forgotBuckets;
    private final Cache<String, Bucket> resetBuckets;

    public PasswordResetRateLimiter(PasswordResetProperties properties) {
        this.properties = properties;
        Duration window = Duration.ofMinutes(properties.getWindowMinutes());
        this.forgotBuckets = newCache(window);
        this.resetBuckets = newCache(window);
    }

    public void checkForgot(String email, String ip) {
        consume("e:" + email + "|" + ip, properties.getMaxRequestsPerEmailPerIp(), forgotBuckets);
        consume("i:" + ip, properties.getMaxRequestsPerIp(), forgotBuckets);
    }

    public void checkReset(String ip) {
        consume("i:" + ip, properties.getMaxResetAttemptsPerIp(), resetBuckets);
    }

    private void consume(String key, int capacity, Cache<String, Bucket> cache) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(properties.getWindowMinutes()))
                .build();
        ConsumptionProbe probe = RateLimiterSupport.resolveBucket(key, bandwidth, cache).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new TooManyRequestsException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }

    private static Cache<String, Bucket> newCache(Duration window) {
        return Caffeine.newBuilder().expireAfterAccess(window).maximumSize(MAX_CACHE_SIZE).build();
    }
}
