package ar.edu.utn.frc.siga.roomrequest.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomRequestForbiddenException")
class RoomRequestForbiddenExceptionTest {

    @Test
    @DisplayName("el detalle usa el texto de la acción y la propiedad action conserva el código")
    void detailUsesTheActionTextAndPropertyKeepsTheCode() {
        RoomRequestForbiddenException e = new RoomRequestForbiddenException("TAKE_OVER", "tomar a cargo");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getMessage())
                .isEqualTo("Tu rol no puede tomar a cargo este pedido en su estado o edificio actual.");
        assertThat(e.getMessage()).doesNotContain("TAKE_OVER");
        assertThat(e.getProperties()).containsEntry("action", "TAKE_OVER");
    }
}
