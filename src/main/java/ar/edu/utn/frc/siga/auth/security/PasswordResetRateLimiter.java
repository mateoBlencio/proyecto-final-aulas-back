package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.config.PasswordResetProperties;
import ar.edu.utn.frc.siga.auth.exception.TooManyRequestsException;
import ar.edu.utn.frc.siga.common.web.RateLimiterSupport;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRateLimiter {

    private static final int MAX_CACHE_SIZE = 10_000;

    private final PasswordResetProperties properties;
    private final Cache<String, Bucket> forgotBuckets;
    private final Cache<String, Bucket> resetBuckets;
    private final Object forgotLock = new Object();

    public PasswordResetRateLimiter(PasswordResetProperties properties) {
        this.properties = properties;
        Duration window = Duration.ofMinutes(properties.getWindowMinutes());
        this.forgotBuckets = newCache(window);
        this.resetBuckets = newCache(window);
    }

    public void checkForgot(String email, String ip) {
        String clientIp = ClientIps.normalize(ip);
        List<Bucket> buckets = List.of(
                bucket("i:" + clientIp, properties.getMaxRequestsPerIp(), forgotBuckets),
                bucket("e:" + email + "|" + clientIp, properties.getMaxRequestsPerEmailPerIp(), forgotBuckets),
                bucket("m:" + email, properties.getMaxRequestsPerEmail(), forgotBuckets));
        synchronized (forgotLock) {
            long waitNanos = buckets.stream()
                    .map(bucket -> bucket.estimateAbilityToConsume(1))
                    .filter(estimation -> !estimation.canBeConsumed())
                    .mapToLong(EstimationProbe::getNanosToWaitForRefill)
                    .max()
                    .orElse(0);
            if (waitNanos > 0) {
                throw new TooManyRequestsException(Duration.ofNanos(waitNanos));
            }
            buckets.forEach(bucket -> bucket.tryConsume(1));
        }
    }

    public void checkReset(String ip) {
        Bucket bucket = bucket("i:" + ClientIps.normalize(ip), properties.getMaxResetAttemptsPerIp(), resetBuckets);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new TooManyRequestsException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }

    private Bucket bucket(String key, int capacity, Cache<String, Bucket> cache) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(properties.getWindowMinutes()))
                .build();
        return RateLimiterSupport.resolveBucket(key, bandwidth, cache);
    }

    private static Cache<String, Bucket> newCache(Duration window) {
        return Caffeine.newBuilder().expireAfterAccess(window).maximumSize(MAX_CACHE_SIZE).build();
    }
}
