package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.config.LoginRateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final LoginRateLimitProperties properties;

    private final Cache<String, LoginRateLimitRecord> records = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    public boolean isRateLimited(String email, Instant now) {
        LoginRateLimitRecord record = records.getIfPresent(email);
        if (record == null) {
            return false;
        }
        return record.isRateLimited(now, Duration.ofMinutes(properties.getWindowMinutes()), properties.getMaxAttempts());
    }

    public void recordFailure(String email, Instant now) {
        LoginRateLimitRecord record = records.get(email, k -> new LoginRateLimitRecord());
        record.recordFailure(now, Duration.ofMinutes(properties.getWindowMinutes()));
    }

    public void recordSuccess(String email) {
        LoginRateLimitRecord record = records.getIfPresent(email);
        if (record != null) {
            record.reset();
        }
    }
}
