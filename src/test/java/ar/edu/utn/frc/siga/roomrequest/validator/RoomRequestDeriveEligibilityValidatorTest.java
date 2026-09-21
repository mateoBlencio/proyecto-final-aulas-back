package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RoomRequestDeriveEligibilityValidator")
class RoomRequestDeriveEligibilityValidatorTest {

    private final RoomRequestDeriveEligibilityValidator validator = new RoomRequestDeriveEligibilityValidator();

    @Test
    @DisplayName("pide computadoras: se puede derivar")
    void requiereComputadoras() {
        RoomRequestItem item = RoomRequestItem.builder().requiresComputers(true).build();
        assertThatCode(() -> validator.validate(item)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pide software específico: se puede derivar")
    void requiereSoftware() {
        RoomRequestItem item = RoomRequestItem.builder().requiredSoftware("MATLAB").build();
        assertThatCode(() -> validator.validate(item)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pide uso exclusivo para examen: se puede derivar")
    void requiereExamUsers() {
        RoomRequestItem item = RoomRequestItem.builder().requiresExamUsers(true).build();
        assertThatCode(() -> validator.validate(item)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin ningún requisito especial: se rechaza")
    void sinRequisitosEspeciales() {
        RoomRequestItem item = RoomRequestItem.builder().build();
        assertThatThrownBy(() -> validator.validate(item)).isInstanceOf(InvalidRoomRequestException.class);
    }
}
