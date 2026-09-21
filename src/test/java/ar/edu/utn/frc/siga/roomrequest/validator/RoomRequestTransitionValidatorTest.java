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
    @DisplayName("desde NEW se puede derivar, pasar a evaluación o cancelar")
    void fromNew() {
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.NEW, RoomRequestStatus.DERIVED_TO_BUILDING)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.NEW, RoomRequestStatus.IN_EVALUATION)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.NEW, RoomRequestStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateTransition(
                RoomRequestStatus.NEW, RoomRequestStatus.RESOLVED))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
    }

    @Test
    @DisplayName("desde DERIVED_TO_BUILDING se puede asignar, devolver a NEW o cancelar")
    void fromDerivedToBuilding() {
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.DERIVED_TO_BUILDING, RoomRequestStatus.IN_EVALUATION)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.DERIVED_TO_BUILDING, RoomRequestStatus.NEW)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.DERIVED_TO_BUILDING, RoomRequestStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateTransition(
                RoomRequestStatus.DERIVED_TO_BUILDING, RoomRequestStatus.RESOLVED))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
    }

    @Test
    @DisplayName("NEW es la única transición hacia atrás, y solo sale de DERIVED_TO_BUILDING (devolución)")
    void onlyDerivedToBuildingReturnsToPending() {
        for (RoomRequestStatus status : EnumSet.complementOf(EnumSet.of(RoomRequestStatus.DERIVED_TO_BUILDING))) {
            assertThatThrownBy(() -> validator.validateTransition(status, RoomRequestStatus.NEW))
                    .as("%s -> NEW", status)
                    .isInstanceOf(InvalidRoomRequestTransitionException.class);
        }
    }

    @Test
    @DisplayName("un pedido en evaluación se puede reasignar (IN_EVALUATION -> IN_EVALUATION), notificar o cancelar")
    void fromInEvaluation() {
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.IN_EVALUATION, RoomRequestStatus.IN_EVALUATION)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.IN_EVALUATION, RoomRequestStatus.RESOLVED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTransition(
                RoomRequestStatus.IN_EVALUATION, RoomRequestStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateTransition(
                RoomRequestStatus.IN_EVALUATION, RoomRequestStatus.DERIVED_TO_BUILDING))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
    }

    @Test
    @DisplayName("RESOLVED es terminal: no sale hacia ningún estado, ni siquiera CANCELLED")
    void resolvedIsTerminal() {
        for (RoomRequestStatus target : RoomRequestStatus.values()) {
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
    @DisplayName("ningún estado permite quedarse donde está, salvo IN_EVALUATION (reasignar)")
    void selfTransitionsAreRejectedExceptPreApproved() {
        for (RoomRequestStatus status : RoomRequestStatus.values()) {
            if (status == RoomRequestStatus.IN_EVALUATION) {
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
