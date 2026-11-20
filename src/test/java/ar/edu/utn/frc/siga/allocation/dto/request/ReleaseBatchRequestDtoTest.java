package ar.edu.utn.frc.siga.allocation.dto.request;

import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReleaseBatchRequestDto")
class ReleaseBatchRequestDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        factory.close();
    }

    private static List<String> messages(ReleaseReason reason, String note) {
        var items = List.of(new DeallocationTargetRequestDto(List.of(1L), null, null, null));
        return validator.validate(new ReleaseBatchRequestDto(items, reason, note)).stream()
                .map(ConstraintViolation::getMessage).toList();
    }

    @ParameterizedTest
    @EnumSource(value = ReleaseReason.class, names = {"MIRROR_RETIRED", "CLASSROOM_DEACTIVATED"})
    @DisplayName("un motivo interno se rechaza con un mensaje que nombra los motivos en español, sin códigos")
    void internalReasonMessageUsesLabels(ReleaseReason reason) {
        assertThat(messages(reason, "nota")).containsExactly(
                "Los motivos Espejo retirado y Aula o edificio deshabilitado son internos y no se pueden elegir");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("OTHER sin nota se rechaza con 'El motivo Otro requiere una nota'")
    void otherWithoutNoteMessageUsesTheLabel(String note) {
        assertThat(messages(ReleaseReason.OTHER, note)).containsExactly("El motivo Otro requiere una nota");
    }

    @Test
    @DisplayName("OTHER con nota y los motivos elegibles sin nota no tienen violaciones")
    void selectableReasonsAreValid() {
        assertThat(messages(ReleaseReason.OTHER, "motivo libre")).isEmpty();
        assertThat(messages(ReleaseReason.VIRTUAL_CLASS, null)).isEmpty();
        assertThat(messages(ReleaseReason.CANCELLED, null)).isEmpty();
    }
}
