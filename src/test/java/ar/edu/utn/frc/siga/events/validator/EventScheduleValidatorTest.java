package ar.edu.utn.frc.siga.events.validator;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.events.config.EventScheduleProperties;
import ar.edu.utn.frc.siga.events.exception.InvalidCommissionForSubjectException;
import ar.edu.utn.frc.siga.events.exception.InvalidEventScheduleException;
import ar.edu.utn.frc.siga.events.exception.MissingAcademicReferenceException;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventScheduleValidator")
class EventScheduleValidatorTest {

    @Mock
    private SubjectCommissionService subjectCommissionService;

    private EventScheduleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EventScheduleValidator(new EventScheduleProperties(), subjectCommissionService);
    }


    @Test
    @DisplayName("validateBusinessHours: horario dentro de la ventana y fin > inicio → no lanza")
    void validateBusinessHoursHorarioValido() {
        assertThatCode(() -> validator.validateBusinessHours(LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateBusinessHours: fin <= inicio → InvalidEventScheduleException (CA3)")
    void validateBusinessHoursFinNoPosteriorAInicio() {
        assertThatThrownBy(() -> validator.validateBusinessHours(LocalTime.of(10, 0), LocalTime.of(10, 0)))
                .isInstanceOf(InvalidEventScheduleException.class);
    }

    @Test
    @DisplayName("validateBusinessHours: inicio antes de la ventana permitida → InvalidEventScheduleException")
    void validateBusinessHoursAntesDeLaVentana() {
        assertThatThrownBy(() -> validator.validateBusinessHours(LocalTime.of(6, 0), LocalTime.of(9, 0)))
                .isInstanceOf(InvalidEventScheduleException.class);
    }

    @Test
    @DisplayName("validateBusinessHours: fin después de la ventana permitida → InvalidEventScheduleException")
    void validateBusinessHoursDespuesDeLaVentana() {
        assertThatThrownBy(() -> validator.validateBusinessHours(LocalTime.of(22, 30), LocalTime.of(23, 30)))
                .isInstanceOf(InvalidEventScheduleException.class);
    }


    @Test
    @DisplayName("validateAcademicReference: OTRO sin subjectId ni commissionId → no lanza")
    void validateAcademicReferenceOtroSinMateriaNiComision() {
        assertThatCode(() -> validator.validateAcademicReference(UniqueEventKind.OTRO, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAcademicReference: PARCIAL sin subjectId → MissingAcademicReferenceException")
    void validateAcademicReferenceParcialSinSubjectId() {
        assertThatThrownBy(() -> validator.validateAcademicReference(UniqueEventKind.PARCIAL, null, 1L))
                .isInstanceOf(MissingAcademicReferenceException.class);
    }

    @Test
    @DisplayName("validateAcademicReference: OTRO con commissionId pero sin subjectId → MissingAcademicReferenceException (comisión sin materia)")
    void validateAcademicReferenceOtroConCommissionIdSinSubjectId() {
        assertThatThrownBy(() -> validator.validateAcademicReference(UniqueEventKind.OTRO, null, 1L))
                .isInstanceOf(MissingAcademicReferenceException.class);
    }

    @Test
    @DisplayName("validateAcademicReference: EXAMEN_FINAL con subjectId pero sin commissionId → no lanza (commissionId nunca es obligatorio por sí solo)")
    void validateAcademicReferenceExamenFinalSinCommissionId() {
        assertThatCode(() -> validator.validateAcademicReference(UniqueEventKind.EXAMEN_FINAL, 1L, null))
                .doesNotThrowAnyException();
    }


    @Test
    @DisplayName("validateCommissionBelongsToSubject: commissionId null → no consulta la fachada, no lanza")
    void validateCommissionBelongsToSubjectCommissionIdNull() {
        assertThatCode(() -> validator.validateCommissionBelongsToSubject(1L, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateCommissionBelongsToSubject: commissionId no pertenece a subjectId → InvalidCommissionForSubjectException")
    void validateCommissionBelongsToSubjectNoPertenece() {
        when(subjectCommissionService.findBySubjectAndCommission(1L, 2L))
                .thenThrow(ResourceNotFoundException.of("SubjectCommission", "1-2"));

        assertThatThrownBy(() -> validator.validateCommissionBelongsToSubject(1L, 2L))
                .isInstanceOf(InvalidCommissionForSubjectException.class);
    }

    @Test
    @DisplayName("validateCommissionBelongsToSubject: commissionId sí pertenece a subjectId → no lanza")
    void validateCommissionBelongsToSubjectPertenece() {
        when(subjectCommissionService.findBySubjectAndCommission(1L, 1L))
                .thenReturn(new SubjectCommissionResponseDto(1L, 1L, 1L, null, 30));

        assertThatCode(() -> validator.validateCommissionBelongsToSubject(1L, 1L))
                .doesNotThrowAnyException();
    }
}
