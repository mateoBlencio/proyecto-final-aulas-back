package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RoomRequestTransitionValidator")
class RoomRequestTransitionValidatorTest {

    private final RoomRequestTransitionValidator validator = new RoomRequestTransitionValidator();

    @Test
    @DisplayName("desde PENDING se puede pre-aprobar o cancelar")
    void fromPending() {
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.PENDING, RoomRequestStatus.PRE_APPROVED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.PENDING, RoomRequestStatus.CANCELLED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un pedido pre-aprobado sólo puede cancelarse, no volver a pendiente")
    void fromPreApproved() {
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.PRE_APPROVED, RoomRequestStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateTransition(
                RoomRequestStatus.PRE_APPROVED, RoomRequestStatus.PENDING))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
    }

    @Test
    @DisplayName("CANCELLED es terminal: no sale hacia ningún estado, ni hacia sí mismo")
    void cancelledIsTerminal() {
        for (RoomRequestStatus target : RoomRequestStatus.values()) {
            assertThatThrownBy(() -> validator.validateTransition(RoomRequestStatus.CANCELLED, target))
                    .as("CANCELLED -> %s", target)
                    .isInstanceOf(InvalidRoomRequestTransitionException.class);
        }
        assertThat(RoomRequestStatus.CANCELLED.isFinal()).isTrue();
    }

    @Test
    @DisplayName("ningún estado permite quedarse donde está")
    void selfTransitionsAreRejected() {
        for (RoomRequestStatus status : RoomRequestStatus.values()) {
            assertThatThrownBy(() -> validator.validateTransition(status, status))
                    .as("%s -> %s", status, status)
                    .isInstanceOf(InvalidRoomRequestTransitionException.class);
        }
    }
}
