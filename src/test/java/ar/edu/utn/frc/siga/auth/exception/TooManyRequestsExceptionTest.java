package ar.edu.utn.frc.siga.auth.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@DisplayName("TooManyRequestsException")
class TooManyRequestsExceptionTest {

    @Test
    @DisplayName("429, título, Retry-After en header y sin properties")
    void statusAndHeader() {
        TooManyRequestsException e = new TooManyRequestsException(Duration.ofSeconds(30));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(e.getTitle()).isEqualTo("Too many requests");
        assertThat(e.getHeaders()).containsEntry(HttpHeaders.RETRY_AFTER, "30");
        assertThat(e.getProperties()).isEmpty();
        assertThat(e.getMessage()).contains("30 segundos");
    }

    @Test
    @DisplayName("Retry-After se redondea hacia arriba")
    void roundsUp() {
        assertThat(new TooManyRequestsException(Duration.ofMillis(1500)).getHeaders())
                .containsEntry(HttpHeaders.RETRY_AFTER, "2");
        assertThat(new TooManyRequestsException(Duration.ofSeconds(1).plusNanos(1)).getHeaders())
                .containsEntry(HttpHeaders.RETRY_AFTER, "2");
    }

    @Test
    @DisplayName("duración cero o menor a un segundo da al menos 1")
    void minimumOneSecond() {
        assertThat(new TooManyRequestsException(Duration.ZERO).getHeaders())
                .containsEntry(HttpHeaders.RETRY_AFTER, "1");
        assertThat(new TooManyRequestsException(Duration.ofMillis(200)).getHeaders())
                .containsEntry(HttpHeaders.RETRY_AFTER, "1");
    }
}
