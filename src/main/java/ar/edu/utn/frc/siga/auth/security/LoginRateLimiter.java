package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.config.LoginRateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final LoginRateLimitProperties properties;

    private final Cache<String, LoginRateLimitRecord> records = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    // Two blocking counters: email+ip (so a third party cannot lock someone else's account from another
    // address) and ip alone with a higher cap (so one address cannot spray many emails). A third counter
    // per email never blocks: it only logs a WARN past a threshold, to detect distributed brute force.
    private static String emailKey(String email, String ip) {
        return "e:" + normalizeEmail(email) + "|" + normalizeIp(ip);
    }

    private static String ipKey(String ip) {
        return "i:" + normalizeIp(ip);
    }

    private static String alertKey(String email) {
        return "a:" + normalizeEmail(email);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // IPv6 clients usually own a whole /64, so rotating the lower 64 bits must not evade the counter
    private static String normalizeIp(String ip) {
        if (ip == null || !ip.contains(":")) {
            return ip;
        }
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            if (bytes.length != 16) {
                return ip;
            }
            StringBuilder prefix = new StringBuilder("v6:");
            for (int i = 0; i < 8; i++) {
                prefix.append(String.format("%02x", bytes[i]));
            }
            return prefix.append("/64").toString();
        } catch (UnknownHostException e) {
            return ip;
        }
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
        int perEmail = records.get(alertKey(email), k -> new LoginRateLimitRecord()).recordFailure(now, window);
        if (perEmail == properties.getAlertThresholdPerEmail()) {
            log.warn("Posible fuerza bruta distribuida: {} fallos de login para un mismo email en {} min",
                    perEmail, properties.getWindowMinutes());
        }
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
