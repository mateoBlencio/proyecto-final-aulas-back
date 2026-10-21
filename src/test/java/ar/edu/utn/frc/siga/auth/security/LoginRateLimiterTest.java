package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.config.LoginRateLimitProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private static final String EMAIL = "admin@frc.utn.edu.ar";
    private static final String IP = "10.0.0.1";

    private LoginRateLimiter rateLimiter;
    private Instant now;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        properties.setMaxAttempts(5);
        properties.setMaxAttemptsPerIp(50);
        properties.setWindowMinutes(15);
        rateLimiter = new LoginRateLimiter(properties);
        now = Instant.parse("2026-01-01T00:00:00Z");
    }

    @Test
    void isRateLimited_shouldBeFalse_beforeReachingMaxAttempts() {
        for (int i = 0; i < 4; i++) {
            rateLimiter.recordFailure(EMAIL, IP, now);
        }
        assertThat(rateLimiter.isRateLimited(EMAIL, IP, now)).isFalse();
    }

    @Test
    void isRateLimited_shouldBeTrue_afterFifthFailure_blockingTheSixthAttempt() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, IP, now);
        }
        assertThat(rateLimiter.isRateLimited(EMAIL, IP, now)).isTrue();
    }

    @Test
    void isRateLimited_shouldBeFalseAgain_afterSlidingWindowExpires() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, IP, now);
        }
        assertThat(rateLimiter.isRateLimited(EMAIL, IP, now)).isTrue();

        Instant afterWindow = now.plusSeconds(16 * 60);
        assertThat(rateLimiter.isRateLimited(EMAIL, IP, afterWindow)).isFalse();
    }

    @Test
    void recordSuccess_shouldResetCounter_evenWithinWindow() {
        for (int i = 0; i < 4; i++) {
            rateLimiter.recordFailure(EMAIL, IP, now);
        }
        rateLimiter.recordSuccess(EMAIL, IP);

        for (int i = 0; i < 4; i++) {
            rateLimiter.recordFailure(EMAIL, IP, now);
        }
        assertThat(rateLimiter.isRateLimited(EMAIL, IP, now)).isFalse();
    }

    @Test
    void retryAfter_shouldBeZero_whenThereAreNoFailures() {
        assertThat(rateLimiter.retryAfter(EMAIL, IP, now)).isEqualTo(Duration.ZERO);
    }

    @Test
    void retryAfter_shouldBeZero_beforeReachingMaxAttempts() {
        for (int i = 0; i < 4; i++) {
            rateLimiter.recordFailure(EMAIL, IP, now);
        }
        assertThat(rateLimiter.retryAfter(EMAIL, IP, now)).isEqualTo(Duration.ZERO);
    }

    @Test
    void retryAfter_shouldBeTheRemainingWindowOfTheOldestFailure_whenBlocked() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, IP, now.plusSeconds(i * 60L));
        }
        Instant later = now.plusSeconds(5 * 60);

        assertThat(rateLimiter.isRateLimited(EMAIL, IP, later)).isTrue();
        assertThat(rateLimiter.retryAfter(EMAIL, IP, later)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void retryAfter_shouldReachZeroExactlyWhenTheBlockEnds() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, IP, now.plusSeconds(i * 60L));
        }
        Instant afterOldestExpires = now.plusSeconds(15 * 60 + 1);

        assertThat(rateLimiter.isRateLimited(EMAIL, IP, afterOldestExpires)).isFalse();
        assertThat(rateLimiter.retryAfter(EMAIL, IP, afterOldestExpires)).isEqualTo(Duration.ZERO);
    }

    @Test
    void isRateLimited_shouldNotBlockTheVictim_whenAnAttackerFailsFromAnotherIp() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, "203.0.113.9", now);
        }

        assertThat(rateLimiter.isRateLimited(EMAIL, "203.0.113.9", now)).isTrue();
        assertThat(rateLimiter.isRateLimited(EMAIL, IP, now)).isFalse();
    }

    @Test
    void isRateLimited_shouldBlockAnIp_afterTooManyFailuresAcrossDifferentEmails() {
        for (int i = 0; i < 50; i++) {
            rateLimiter.recordFailure("user" + i + "@frc.utn.edu.ar", IP, now);
        }

        assertThat(rateLimiter.isRateLimited("other@frc.utn.edu.ar", IP, now)).isTrue();
        assertThat(rateLimiter.isRateLimited("other@frc.utn.edu.ar", "10.0.0.2", now)).isFalse();
    }

    @Test
    void recordSuccess_shouldNotResetTheIpCounter() {
        for (int i = 0; i < 49; i++) {
            rateLimiter.recordFailure("user" + i + "@frc.utn.edu.ar", IP, now);
        }
        rateLimiter.recordSuccess("mine@frc.utn.edu.ar", IP);
        rateLimiter.recordFailure("last@frc.utn.edu.ar", IP, now);

        assertThat(rateLimiter.isRateLimited("other@frc.utn.edu.ar", IP, now)).isTrue();
    }

    @Test
    void isRateLimited_shouldBlockAnIp_afterFiftyOneDistinctEmails() {
        for (int i = 0; i < 51; i++) {
            rateLimiter.recordFailure("user" + i + "@frc.utn.edu.ar", IP, now);
        }

        assertThat(rateLimiter.isRateLimited("fresh@frc.utn.edu.ar", IP, now)).isTrue();
        assertThat(rateLimiter.isRateLimited("fresh@frc.utn.edu.ar", "10.0.0.2", now)).isFalse();
    }

    @Test
    void isRateLimited_shouldTreatEmailCaseAndSpacesAsTheSameAccount() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(i % 2 == 0 ? "ADMIN@frc.utn.edu.ar" : " Admin@frc.utn.edu.ar ", IP, now);
        }

        assertThat(rateLimiter.isRateLimited(EMAIL, IP, now)).isTrue();
    }

    @Test
    void isRateLimited_shouldGroupIpv6AddressesOfTheSame64Prefix() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(EMAIL, "2001:db8:1:2::" + (i + 1), now);
        }

        assertThat(rateLimiter.isRateLimited(EMAIL, "2001:db8:1:2:aaaa:bbbb:cccc:dddd", now)).isTrue();
        assertThat(rateLimiter.isRateLimited(EMAIL, "2001:db8:1:3::1", now)).isFalse();
    }

    @Test
    void emailAlertCounter_shouldWarnAtThresholdWithoutBlocking() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoginRateLimiter.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 50; i++) {
                rateLimiter.recordFailure(EMAIL, "10.1." + (i / 250) + "." + (i % 250 + 1), now);
            }

            assertThat(appender.list).extracting(e -> e.getFormattedMessage())
                    .anyMatch(m -> m.contains("fuerza bruta distribuida"));
            assertThat(rateLimiter.isRateLimited(EMAIL, "10.9.9.9", now)).isFalse();
        } finally {
            logger.detachAppender(appender);
        }
    }
}
