package ar.edu.utn.frc.siga.auth.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("FieldValidationException")
class FieldValidationExceptionTest {

    @Test
    @DisplayName("un mensaje: 400, título y errors por campo con una lista de un elemento")
    void singleMessage() {
        FieldValidationException e = new FieldValidationException("password", "muy corta");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getTitle()).isEqualTo("Validación fallida");
        assertThat(e.getProperties()).containsOnlyKeys("errors");
        assertThat(e.getProperties().get("errors")).isEqualTo(Map.of("password", List.of("muy corta")));
    }

    @Test
    @DisplayName("varios mensajes: se conservan en orden bajo el mismo campo")
    void multipleMessages() {
        FieldValidationException e = new FieldValidationException("password", List.of("a", "b"));

        assertThat(e.getProperties().get("errors")).isEqualTo(Map.of("password", List.of("a", "b")));
        assertThat(e.getHeaders()).isEmpty();
    }
}
