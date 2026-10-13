package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.config.LoginRateLimitProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private static final String EMAIL = "admin@frc.utn.edu.ar";

    private LoginRateLimiter rateLimiter;
    private Instant now;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        properties.setMaxAttempts(5);
        properties.setWindowMinutes(15);
        rateLimiter = new LoginRateLimiter(properties);
        now = Instant.parse("2026-01-01T00:00:00Z");
    }

    @Test
    void isRateLimited_shouldBeFalse_beforeReachingMaxAttempts() {
        for (int i = 0; i < 4; i++) {
            rateLimiter.recordFailure(EMAIL, now);
        }
        assertThat(rateLimiter.isRateLimited(EMAIL, now)).isFalse();
    }

    @Test
    void isRateLimited_shouldBeTrue_afterFifthFailure_blockingTheSixthAttempt() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, now);
        }
        assertThat(rateLimiter.isRateLimited(EMAIL, now)).isTrue();
    }

    @Test
    void isRateLimited_shouldBeFalseAgain_afterSlidingWindowExpires() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, now);
        }
        assertThat(rateLimiter.isRateLimited(EMAIL, now)).isTrue();

        Instant afterWindow = now.plusSeconds(16 * 60);
        assertThat(rateLimiter.isRateLimited(EMAIL, afterWindow)).isFalse();
    }

    @Test
    void recordSuccess_shouldResetCounter_evenWithinWindow() {
        for (int i = 0; i < 4; i++) {
            rateLimiter.recordFailure(EMAIL, now);
        }
        rateLimiter.recordSuccess(EMAIL);

        for (int i = 0; i < 4; i++) {
            rateLimiter.recordFailure(EMAIL, now);
        }
        assertThat(rateLimiter.isRateLimited(EMAIL, now)).isFalse();
    }

    @Test
    void retryAfter_shouldBeZero_whenThereAreNoFailures() {
        assertThat(rateLimiter.retryAfter(EMAIL, now)).isEqualTo(Duration.ZERO);
    }

    @Test
    void retryAfter_shouldBeZero_beforeReachingMaxAttempts() {
        for (int i = 0; i < 4; i++) {
            rateLimiter.recordFailure(EMAIL, now);
        }
        assertThat(rateLimiter.retryAfter(EMAIL, now)).isEqualTo(Duration.ZERO);
    }

    @Test
    void retryAfter_shouldBeTheRemainingWindowOfTheOldestFailure_whenBlocked() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, now.plusSeconds(i * 60L));
        }
        Instant later = now.plusSeconds(5 * 60);

        assertThat(rateLimiter.isRateLimited(EMAIL, later)).isTrue();
        assertThat(rateLimiter.retryAfter(EMAIL, later)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void retryAfter_shouldReachZeroExactlyWhenTheBlockEnds() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, now.plusSeconds(i * 60L));
        }
        Instant afterOldestExpires = now.plusSeconds(15 * 60 + 1);

        assertThat(rateLimiter.isRateLimited(EMAIL, afterOldestExpires)).isFalse();
        assertThat(rateLimiter.retryAfter(EMAIL, afterOldestExpires)).isEqualTo(Duration.ZERO);
    }
}
