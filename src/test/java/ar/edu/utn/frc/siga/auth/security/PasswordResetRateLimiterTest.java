package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.config.PasswordResetProperties;
import ar.edu.utn.frc.siga.auth.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordResetRateLimiter")
class PasswordResetRateLimiterTest {

    private static final String EMAIL = "ana.gomez@frc.utn.edu.ar";

    private PasswordResetRateLimiter limiter;

    @BeforeEach
    void setUp() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setFrontendBaseUrl("https://siga.frc.utn.edu.ar");
        limiter = new PasswordResetRateLimiter(properties);
    }

    @Test
    @DisplayName("el sexto pedido del mismo correo desde IPs distintas da 429 con Retry-After")
    void sixthRequestForTheSameEmailFromDifferentIpsIsRejected() {
        for (int i = 1; i <= 5; i++) {
            limiter.checkForgot(EMAIL, "203.0.113." + i);
        }

        assertThatThrownBy(() -> limiter.checkForgot(EMAIL, "203.0.113.6"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> {
                    assertThat(e.getHeaders()).containsKey(HttpHeaders.RETRY_AFTER);
                    assertThat(Long.parseLong(e.getHeaders().get(HttpHeaders.RETRY_AFTER))).isPositive();
                });
    }

    @Test
    @DisplayName("el tope por correo no afecta a otro correo")
    void emailCapDoesNotAffectAnotherEmail() {
        for (int i = 1; i <= 5; i++) {
            limiter.checkForgot(EMAIL, "203.0.113." + i);
        }

        assertThatCode(() -> limiter.checkForgot("otra.persona@frc.utn.edu.ar", "203.0.113.7"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("forgot: el cuarto pedido del mismo correo desde la misma IP IPv4 da 429")
    void fourthRequestForTheSameEmailAndIpIsRejected() {
        for (int i = 0; i < 3; i++) {
            limiter.checkForgot(EMAIL, "203.0.113.1");
        }

        assertThatThrownBy(() -> limiter.checkForgot(EMAIL, "203.0.113.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    @DisplayName("forgot: dos IPv6 del mismo /64 comparten el bucket de correo e IP")
    void sameIpv6PrefixSharesTheEmailAndIpBucket() {
        limiter.checkForgot(EMAIL, "2001:db8:1:2::1");
        limiter.checkForgot(EMAIL, "2001:db8:1:2::2");
        limiter.checkForgot(EMAIL, "2001:db8:1:2:aaaa:bbbb:cccc:dddd");

        assertThatThrownBy(() -> limiter.checkForgot(EMAIL, "2001:db8:1:2:1111:2222:3333:4444"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    @DisplayName("forgot: un IPv6 de otro /64 no comparte el bucket de correo e IP")
    void differentIpv6PrefixHasItsOwnEmailAndIpBucket() {
        for (int i = 1; i <= 3; i++) {
            limiter.checkForgot(EMAIL, "2001:db8:1:2::" + i);
        }

        assertThatCode(() -> limiter.checkForgot(EMAIL, "2001:db8:1:3::1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("forgot: el tope por IP cuenta todo el /64 aunque cada pedido use otro correo y otro host")
    void perIpCapCountsTheWholeIpv6Prefix() {
        for (int i = 0; i < 20; i++) {
            limiter.checkForgot("persona" + i + "@frc.utn.edu.ar", "2001:db8:1:2::" + (i + 1));
        }

        assertThatThrownBy(() -> limiter.checkForgot("persona99@frc.utn.edu.ar", "2001:db8:1:2:ffff::1"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.checkForgot("persona99@frc.utn.edu.ar", "2001:db8:1:3::1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reset: dos IPv6 del mismo /64 comparten el bucket por IP")
    void resetSharesTheBucketAcrossAnIpv6Prefix() {
        for (int i = 0; i < 10; i++) {
            limiter.checkReset("2001:db8:1:2::" + (i + 1));
        }

        assertThatThrownBy(() -> limiter.checkReset("2001:db8:1:2:aaaa:bbbb:cccc:dddd"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.checkReset("2001:db8:1:3::1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reset: el undécimo intento desde la misma IPv4 da 429")
    void eleventhResetAttemptFromTheSameIpv4IsRejected() {
        for (int i = 0; i < 10; i++) {
            limiter.checkReset("203.0.113.1");
        }

        assertThatThrownBy(() -> limiter.checkReset("203.0.113.1")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.checkReset("203.0.113.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("forgot: los 429 por tope de IP no descuentan el cupo del correo para otra IP")
    void rejectionsByIpCapDoNotConsumeTheEmailQuota() {
        for (int i = 0; i < 20; i++) {
            limiter.checkForgot("persona" + i + "@frc.utn.edu.ar", "198.51.100.1");
        }
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> limiter.checkForgot(EMAIL, "198.51.100.1"))
                    .isInstanceOf(TooManyRequestsException.class);
        }

        assertThatCode(() -> limiter.checkForgot(EMAIL, "203.0.113.50")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("forgot: un 429 por tope de correo e IP no descuenta el cupo del correo")
    void rejectionByEmailAndIpCapDoesNotConsumeTheEmailQuota() {
        for (int i = 0; i < 3; i++) {
            limiter.checkForgot(EMAIL, "203.0.113.1");
        }
        assertThatThrownBy(() -> limiter.checkForgot(EMAIL, "203.0.113.1"))
                .isInstanceOf(TooManyRequestsException.class);

        assertThatCode(() -> limiter.checkForgot(EMAIL, "203.0.113.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkForgot(EMAIL, "203.0.113.3")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkForgot(EMAIL, "203.0.113.4"))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
