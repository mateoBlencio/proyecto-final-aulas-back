package ar.edu.utn.frc.siga.roomrequest.exception;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidRoomRequestTransitionExceptionTest {

    @Test
    @DisplayName("el mensaje usa las etiquetas en español de los estados, no sus nombres internos")
    void mensajeConEtiquetas() {
        InvalidRoomRequestTransitionException ex =
                new InvalidRoomRequestTransitionException(RoomRequestStatus.RESOLVED, RoomRequestStatus.NEW);

        assertThat(ex.getMessage()).isEqualTo("No se puede pasar la solicitud de \"Resuelto\" a \"Nuevo\".");
        assertThat(ex.getProperties()).containsEntry("currentStatus", RoomRequestStatus.RESOLVED)
                .containsEntry("targetStatus", RoomRequestStatus.NEW);
    }
}
