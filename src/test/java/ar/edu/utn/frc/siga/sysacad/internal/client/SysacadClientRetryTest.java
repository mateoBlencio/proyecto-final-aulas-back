package ar.edu.utn.frc.siga.sysacad.internal.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SysacadClient: decisión de reintento")
class SysacadClientRetryTest {

    @Test
    @DisplayName("isRetryable: 429 y 5xx sí, 4xx del cliente no")
    void decideQueEstadosSeReintentan() {
        assertThat(SysacadClient.isRetryable(HttpStatus.TOO_MANY_REQUESTS)).isTrue();
        assertThat(SysacadClient.isRetryable(HttpStatus.INTERNAL_SERVER_ERROR)).isTrue();
        assertThat(SysacadClient.isRetryable(HttpStatus.SERVICE_UNAVAILABLE)).isTrue();
        assertThat(SysacadClient.isRetryable(HttpStatus.UNAUTHORIZED)).isFalse();
        assertThat(SysacadClient.isRetryable(HttpStatus.NOT_FOUND)).isFalse();
    }

    @Test
    @DisplayName("backoffFor: sin Retry-After crece exponencialmente")
    void creceExponencialmenteSinRetryAfter() {
        assertThat(SysacadClient.backoffFor(new HttpHeaders(), 0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(SysacadClient.backoffFor(new HttpHeaders(), 1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(SysacadClient.backoffFor(new HttpHeaders(), 2)).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    @DisplayName("backoffFor: respeta Retry-After en segundos")
    void respetaRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "12");

        assertThat(SysacadClient.backoffFor(headers, 0)).isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    @DisplayName("backoffFor: Retry-After con fecha HTTP cae al backoff propio")
    void ignoraRetryAfterNoNumerico() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "Wed, 21 Oct 2026 07:28:00 GMT");

        assertThat(SysacadClient.backoffFor(headers, 1)).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("backoffFor: nunca supera el tope de 30s")
    void acotaElBackoff() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "600");

        assertThat(SysacadClient.backoffFor(headers, 0)).isEqualTo(Duration.ofSeconds(30));
        assertThat(SysacadClient.backoffFor(new HttpHeaders(), 10)).isEqualTo(Duration.ofSeconds(30));
    }
}
