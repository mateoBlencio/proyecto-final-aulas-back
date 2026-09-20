package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RoomRequestTransitionValidator")
class RoomRequestTransitionValidatorTest {

    private final RoomRequestTransitionValidator validator = new RoomRequestTransitionValidator();

    @Test
    @DisplayName("desde PENDING se puede derivar, pre-aprobar o cancelar")
    void fromPending() {
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.PENDING, RoomRequestStatus.DERIVED_TO_BUILDING)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.PENDING, RoomRequestStatus.PRE_APPROVED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.PENDING, RoomRequestStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateTransition(
                RoomRequestStatus.PENDING, RoomRequestStatus.RESOLVED))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
    }

    @Test
    @DisplayName("desde DERIVED_TO_BUILDING se puede asignar, devolver a PENDING o cancelar")
    void fromDerivedToBuilding() {
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.DERIVED_TO_BUILDING, RoomRequestStatus.PRE_APPROVED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.DERIVED_TO_BUILDING, RoomRequestStatus.PENDING)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.DERIVED_TO_BUILDING, RoomRequestStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateTransition(
                RoomRequestStatus.DERIVED_TO_BUILDING, RoomRequestStatus.RESOLVED))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
    }

    @Test
    @DisplayName("PENDING es la única transición hacia atrás, y solo sale de DERIVED_TO_BUILDING (devolución)")
    void onlyDerivedToBuildingReturnsToPending() {
        for (RoomRequestStatus status : EnumSet.complementOf(EnumSet.of(RoomRequestStatus.DERIVED_TO_BUILDING))) {
            assertThatThrownBy(() -> validator.validateTransition(status, RoomRequestStatus.PENDING))
                    .as("%s -> PENDING", status)
                    .isInstanceOf(InvalidRoomRequestTransitionException.class);
        }
    }

    @Test
    @DisplayName("un pedido pre-aprobado se puede reasignar (PRE_APPROVED -> PRE_APPROVED), notificar o cancelar")
    void fromPreApproved() {
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.PRE_APPROVED, RoomRequestStatus.PRE_APPROVED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.PRE_APPROVED, RoomRequestStatus.RESOLVED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.PRE_APPROVED, RoomRequestStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateTransition(
                RoomRequestStatus.PRE_APPROVED, RoomRequestStatus.DERIVED_TO_BUILDING))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
    }

    @Test
    @DisplayName("un pedido resuelto queda congelado: solo puede cancelarse")
    void fromResolved() {
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.RESOLVED, RoomRequestStatus.CANCELLED)).doesNotThrowAnyException();
        for (RoomRequestStatus target : EnumSet.complementOf(EnumSet.of(RoomRequestStatus.CANCELLED))) {
            assertThatThrownBy(() -> validator.validateTransition(RoomRequestStatus.RESOLVED, target))
                    .as("RESOLVED -> %s", target)
                    .isInstanceOf(InvalidRoomRequestTransitionException.class);
        }
    }

    @Test
    @DisplayName("CANCELLED es terminal: no sale hacia ningún estado, ni hacia sí mismo")
    void cancelledIsTerminal() {
        for (RoomRequestStatus target : RoomRequestStatus.values()) {
            assertThatThrownBy(() -> validator.validateTransition(RoomRequestStatus.CANCELLED, target))
                    .as("CANCELLED -> %s", target)
                    .isInstanceOf(InvalidRoomRequestTransitionException.class);
        }
        assertThat(RoomRequestStatus.CANCELLED.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("isCancelled() sólo es true para CANCELLED")
    void isCancelledOnlyForCancelled() {
        Set<RoomRequestStatus> cancelled = EnumSet.of(RoomRequestStatus.CANCELLED);
        for (RoomRequestStatus status : RoomRequestStatus.values()) {
            assertThat(status.isCancelled()).as(status.name()).isEqualTo(cancelled.contains(status));
        }
    }

    @Test
    @DisplayName("ningún estado permite quedarse donde está, salvo PRE_APPROVED (reasignar)")
    void selfTransitionsAreRejectedExceptPreApproved() {
        for (RoomRequestStatus status : RoomRequestStatus.values()) {
            if (status == RoomRequestStatus.PRE_APPROVED) {
                assertThatCode(() -> validator.validateTransition(status, status))
                        .as("%s -> %s", status, status)
                        .doesNotThrowAnyException();
                continue;
            }
            assertThatThrownBy(() -> validator.validateTransition(status, status))
                    .as("%s -> %s", status, status)
                    .isInstanceOf(InvalidRoomRequestTransitionException.class);
        }
    }
}
