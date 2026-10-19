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

    // Two counters: email+ip (so a third party cannot lock someone else's account from another
    // address) and ip alone with a higher cap (so one address cannot spray many emails).
    private static String emailKey(String email, String ip) {
        return "e:" + email + "|" + ip;
    }

    private static String ipKey(String ip) {
        return "i:" + ip;
    }

    public boolean isRateLimited(String email, String ip, Instant now) {
        return limited(emailKey(email, ip), properties.getMaxAttempts(), now)
                || limited(ipKey(ip), properties.getMaxAttemptsPerIp(), now);
    }

    public Duration retryAfter(String email, String ip, Instant now) {
        Duration byEmail = retry(emailKey(email, ip), properties.getMaxAttempts(), now);
        Duration byIp = retry(ipKey(ip), properties.getMaxAttemptsPerIp(), now);
        return byEmail.compareTo(byIp) >= 0 ? byEmail : byIp;
    }

    public void recordFailure(String email, String ip, Instant now) {
        Duration window = window();
        records.get(emailKey(email, ip), k -> new LoginRateLimitRecord()).recordFailure(now, window);
        records.get(ipKey(ip), k -> new LoginRateLimitRecord()).recordFailure(now, window);
    }

    // Only the email+ip counter resets: a success must not clear the ip counter, otherwise an
    // attacker with their own valid account could reset it between guesses.
    public void recordSuccess(String email, String ip) {
        LoginRateLimitRecord record = records.getIfPresent(emailKey(email, ip));
        if (record != null) {
            record.reset();
        }
    }

    private boolean limited(String key, int max, Instant now) {
        LoginRateLimitRecord record = records.getIfPresent(key);
        return record != null && record.isRateLimited(now, window(), max);
    }

    private Duration retry(String key, int max, Instant now) {
        LoginRateLimitRecord record = records.getIfPresent(key);
        return record == null ? Duration.ZERO : record.retryAfter(now, window(), max);
    }

    private Duration window() {
        return Duration.ofMinutes(properties.getWindowMinutes());
    }
}
