package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reglas que Bean Validation no puede expresar. Se testean con mocks para
 * cubrir cada rama sin levantar el contexto: los tests de integración sólo
 * pasan por el camino feliz y por un par de rechazos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestValidator")
class RoomRequestValidatorTest {

    private static final Long SUBJECT_ID = 42L;
    private static final Long COMMISSION_ID = 7L;

    @Mock
    private SubjectService subjectService;

    @Mock
    private CommissionService commissionService;

    @Mock
    private SubjectCommissionService subjectCommissionService;

    @Mock
    private ClassroomService classroomService;

    @InjectMocks
    private RoomRequestValidator validator;

    @Nested
    @DisplayName("referencia académica según el tipo")
    class AcademicReference {

        @Test
        @DisplayName("parcial sin materia: rechazado antes de tocar la base")
        void examWithoutSubject_isRejected() {
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, null, item(COMMISSION_ID));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("subjectId es obligatorio");
            verifyNoInteractions(subjectService, commissionService, classroomService);
        }

        @Test
        @DisplayName("parcial con materia pero un pedido sin comisión: rechazado")
        void examWithoutCommission_isRejected() {
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID,
                    item(COMMISSION_ID), item(null));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("commissionId es obligatorio");
        }

        @Test
        @DisplayName("conferencia sin materia ni comisión: aceptada, no se consulta nada")
        void conferenceWithoutAcademicReference_isAccepted() {
            CreateRoomRequestDto dto = request(RoomRequestType.CONFERENCE, null, item(null));

            assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();
            verifyNoInteractions(subjectService, commissionService, subjectCommissionService);
        }

        @Test
        @DisplayName("conferencia con materia: opcional, pero si viene tiene que existir")
        void conferenceWithSubject_stillValidatesIt() {
            CreateRoomRequestDto dto = request(RoomRequestType.CONFERENCE, SUBJECT_ID, item(null));

            assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();
            verify(subjectService).findById(SUBJECT_ID);
        }

        @Test
        @DisplayName("todos los tipos salvo conferencia y OTHER exigen referencia académica")
        void onlyConferenceAndOtherAreExempt() {
            for (RoomRequestType type : RoomRequestType.values()) {
                boolean exempt = type == RoomRequestType.CONFERENCE || type == RoomRequestType.OTHER;
                assertThat(type.requiresAcademicReference())
                        .as("requiresAcademicReference de %s", type)
                        .isEqualTo(!exempt);
            }
        }

        @Test
        @DisplayName("OTHER sin materia ni comisión: aceptado, no se consulta nada")
        void otherWithoutAcademicReference_isAccepted() {
            CreateRoomRequestDto dto = request(RoomRequestType.OTHER, null, otherItem(null, "Necesito un aula para grabar un video institucional"));

            assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();
            verifyNoInteractions(subjectService, commissionService, subjectCommissionService);
        }
    }

    @Nested
    @DisplayName("existencia de las referencias")
    class ReferencesExist {

        @Test
        @DisplayName("materia inexistente: propaga el 404 del módulo academic")
        void unknownSubject_propagates404() {
            when(subjectService.findById(SUBJECT_ID)).thenThrow(ResourceNotFoundException.of("Subject", SUBJECT_ID));
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID, item(COMMISSION_ID));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("comisión inexistente: propaga el 404")
        void unknownCommission_propagates404() {
            when(commissionService.findById(COMMISSION_ID))
                    .thenThrow(ResourceNotFoundException.of("Commission", COMMISSION_ID));
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID, item(COMMISSION_ID));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("la comisión existe pero no es de esa materia: 400 con mensaje explicativo")
        void commissionFromAnotherSubject_isRejected() {
            when(subjectCommissionService.findBySubjectAndCommission(SUBJECT_ID, COMMISSION_ID))
                    .thenThrow(ResourceNotFoundException.of("SubjectCommission", COMMISSION_ID));
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID, item(COMMISSION_ID));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("no pertenece a la materia");
        }

        @Test
        @DisplayName("comisión sin materia (conferencia): no se chequea pertenencia")
        void commissionWithoutSubject_skipsOwnershipCheck() {
            CreateRoomRequestDto dto = request(RoomRequestType.CONFERENCE, null, item(COMMISSION_ID));

            assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();
            verify(commissionService).findById(COMMISSION_ID);
            verifyNoInteractions(subjectCommissionService);
        }
    }

    @Nested
    @DisplayName("existencia de las aulas")
    class Classrooms {

        @Test
        @DisplayName("sin aulas referenciadas: no se consulta el módulo space")
        void noClassrooms_skipsLookup() {
            CreateRoomRequestDto dto = request(RoomRequestType.CONFERENCE, null, item(null));

            validator.validateForCreation(dto);

            verifyNoInteractions(classroomService);
        }

        @Test
        @DisplayName("aula actual y preferidas se consultan juntas en una sola query, sin repetir ids")
        void allClassrooms_areLookedUpInOneBatch() {
            CreateRoomRequestItemDto first = item(null, 10, List.of(11, 12));
            CreateRoomRequestItemDto second = item(null, 11, List.of(12, 13));
            when(classroomService.findByIds(any())).thenReturn(classrooms(10, 11, 12, 13));
            CreateRoomRequestDto dto = request(RoomRequestType.CONFERENCE, null, first, second);

            validator.validateForCreation(dto);

            ArgumentCaptor<Collection<Integer>> captor = ArgumentCaptor.captor();
            verify(classroomService, times(1)).findByIds(captor.capture());
            assertThat(captor.getValue()).containsExactlyInAnyOrder(10, 11, 12, 13);
        }

        @Test
        @DisplayName("aula preferida inexistente: 404 de Classroom")
        void unknownPreferredClassroom_isRejected() {
            when(classroomService.findByIds(any())).thenReturn(classrooms(10));
            CreateRoomRequestDto dto = request(RoomRequestType.CONFERENCE, null,
                    item(null, null, List.of(10, 99)));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Classroom");
        }

        @Test
        @DisplayName("aula actual inexistente: 404 de Classroom")
        void unknownCurrentClassroom_isRejected() {
            when(classroomService.findByIds(any())).thenReturn(List.of());
            CreateRoomRequestDto dto = request(RoomRequestType.CONFERENCE, null, item(null, 99, List.of()));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Classroom");
        }

        @Test
        @DisplayName("no se valida capacidad ni disponibilidad: son preferencias, no una asignación")
        void capacityAndAvailability_areNotValidated() {
            when(classroomService.findByIds(any()))
                    .thenReturn(List.of(new ClassroomResponseDto(10, "A1", 1, 5, false, 1, "P", 1, "Aula")));
            CreateRoomRequestDto dto = request(RoomRequestType.CONFERENCE, null,
                    item(null, null, List.of(10)));

            assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();
        }
    }

    /**
     * La pregunta sólo aplica a un examen con computadoras. Donde aplica es
     * obligatoria; donde no aplica tiene que venir en null. {@code false} y
     * null no son intercambiables: uno es "le preguntamos y dijo que no", el
     * otro es "no aplica".
     */
    @Nested
    @DisplayName("usuarios de examen")
    class ExamUsers {

        @Test
        @DisplayName("parcial con computadoras y respuesta true: se acepta")
        void examWithComputersAndTrue_isAccepted() {
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID,
                    examUsersItem(COMMISSION_ID, true, true));

            assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("parcial con computadoras y respuesta false: se acepta, es una respuesta válida")
        void examWithComputersAndFalse_isAccepted() {
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID,
                    examUsersItem(COMMISSION_ID, true, false));

            assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("parcial con computadoras sin responder: se rechaza, el formulario quedó incompleto")
        void examWithComputersAndNull_isRejected() {
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID,
                    examUsersItem(COMMISSION_ID, true, null));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("obligatorio");
        }

        @Test
        @DisplayName("final con computadoras: rige la misma regla que el parcial")
        void finalExamWithComputers_behavesLikePartial() {
            assertThatCode(() -> validator.validateForCreation(
                    request(RoomRequestType.FINAL_EXAM, SUBJECT_ID, examUsersItem(COMMISSION_ID, true, true))))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> validator.validateForCreation(
                    request(RoomRequestType.FINAL_EXAM, SUBJECT_ID, examUsersItem(COMMISSION_ID, true, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("parcial sin computadoras: la pregunta no aplica, hay que dejarla en null")
        void examWithoutComputers_mustBeNull() {
            assertThatCode(() -> validator.validateForCreation(
                    request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID, examUsersItem(COMMISSION_ID, false, null))))
                    .doesNotThrowAnyException();

            for (Boolean answer : new Boolean[]{true, false}) {
                assertThatThrownBy(() -> validator.validateForCreation(
                        request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID,
                                examUsersItem(COMMISSION_ID, false, answer))))
                        .as("parcial sin computadoras con requiresExamUsers=%s", answer)
                        .isInstanceOf(InvalidRoomRequestException.class)
                        .hasMessageContaining("solo puede indicarse");
            }
        }

        @Test
        @DisplayName("tipo no-examen con computadoras: la pregunta no aplica aunque haya computadoras")
        void nonExamTypeWithComputers_mustBeNull() {
            assertThatCode(() -> validator.validateForCreation(
                    request(RoomRequestType.ONE_TIME_ROOM_CHANGE, SUBJECT_ID,
                            examUsersItem(COMMISSION_ID, true, null))))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> validator.validateForCreation(
                    request(RoomRequestType.ONE_TIME_ROOM_CHANGE, SUBJECT_ID,
                            examUsersItem(COMMISSION_ID, true, true))))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("solo puede indicarse");
        }

        @Test
        @DisplayName("conferencia: null aceptado, y tanto true como false rechazados")
        void conference_mustBeNull() {
            assertThatCode(() -> validator.validateForCreation(
                    request(RoomRequestType.CONFERENCE, null, examUsersItem(null, false, null))))
                    .doesNotThrowAnyException();

            for (Boolean answer : new Boolean[]{true, false}) {
                assertThatThrownBy(() -> validator.validateForCreation(
                        request(RoomRequestType.CONFERENCE, null, examUsersItem(null, false, answer))))
                        .as("conferencia con requiresExamUsers=%s", answer)
                        .isInstanceOf(InvalidRoomRequestException.class);
            }
        }

        @Test
        @DisplayName("la regla se evalúa por pedido: alcanza con que uno esté mal")
        void ruleIsEvaluatedPerItem() {
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID,
                    examUsersItem(COMMISSION_ID, true, true),
                    examUsersItem(COMMISSION_ID, true, null));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("obligatorio");
        }

        @Test
        @DisplayName("sólo parcial y final cuentan como examen")
        void onlyPartialAndFinalAreExams() {
            for (RoomRequestType type : RoomRequestType.values()) {
                assertThat(type.isExam())
                        .as("isExam de %s", type)
                        .isEqualTo(type == RoomRequestType.PARTIAL_EXAM || type == RoomRequestType.FINAL_EXAM);
            }
        }
    }

    @Nested
    @DisplayName("tipo OTHER: sin reglas propias, salvo describir el pedido")
    class OtherType {

        @Test
        @DisplayName("sin observations: se rechaza porque no hay forma de saber de qué se trata")
        void withoutObservations_isRejected() {
            CreateRoomRequestDto dto = request(RoomRequestType.OTHER, null, otherItem(null, null));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("observations");
        }

        @Test
        @DisplayName("con observations en blanco: se rechaza igual que null")
        void withBlankObservations_isRejected() {
            CreateRoomRequestDto dto = request(RoomRequestType.OTHER, null, otherItem(null, "   "));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("observations");
        }

        @Test
        @DisplayName("con observations: se acepta")
        void withObservations_isAccepted() {
            CreateRoomRequestDto dto = request(RoomRequestType.OTHER, null,
                    otherItem(null, "Necesito el aula para un evento no contemplado en los otros tipos"));

            assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("dos pedidos OTHER, uno sin observations: se rechaza igual")
        void oneItemWithoutObservations_rejectsWholeRequest() {
            CreateRoomRequestDto dto = request(RoomRequestType.OTHER, null,
                    otherItem(null, "Primer pedido, con descripción"), otherItem(null, null));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("observations");
        }

        @Test
        @DisplayName("otros tipos no exigen observations")
        void nonOtherTypes_doNotRequireObservations() {
            CreateRoomRequestDto dto = request(RoomRequestType.CONFERENCE, null, item(null));

            assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("transiciones de estado")
    class Transitions {

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

    @Nested
    @DisplayName("orden de las validaciones")
    class ValidationOrder {

        @Test
        @DisplayName("un rechazo por requiresExamUsers no consulta ningún módulo")
        void examUsersRejection_doesNotHitAnyModule() {
            CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID,
                    examUsersItem(COMMISSION_ID, true, null));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class);

            verifyNoInteractions(subjectService, commissionService, subjectCommissionService, classroomService);
        }

        @Test
        @DisplayName("un rechazo por observations faltantes en OTHER no consulta ningún módulo")
        void otherRejection_doesNotHitAnyModule() {
            CreateRoomRequestDto dto = request(RoomRequestType.OTHER, null, otherItem(null, null));

            assertThatThrownBy(() -> validator.validateForCreation(dto))
                    .isInstanceOf(InvalidRoomRequestException.class);

            verifyNoInteractions(subjectService, commissionService, subjectCommissionService, classroomService);
        }
    }

    @Test
    @DisplayName("solicitud válida con todo poblado: no lanza y consulta cada módulo una sola vez")
    void fullyPopulatedRequest_isAccepted() {
        when(classroomService.findByIds(any())).thenReturn(classrooms(10, 11));
        CreateRoomRequestDto dto = request(RoomRequestType.PARTIAL_EXAM, SUBJECT_ID,
                item(COMMISSION_ID, 10, List.of(11)));

        assertThatCode(() -> validator.validateForCreation(dto)).doesNotThrowAnyException();

        verify(subjectService, times(1)).findById(SUBJECT_ID);
        verify(commissionService, times(1)).findById(COMMISSION_ID);
        verify(subjectCommissionService, times(1)).findBySubjectAndCommission(SUBJECT_ID, COMMISSION_ID);
        verify(classroomService, times(1)).findByIds(any());
    }

    private static CreateRoomRequestDto request(RoomRequestType type, Long subjectId,
                                                CreateRoomRequestItemDto... items) {
        return new CreateRoomRequestDto(type, AcademicScope.GRADO, "Ada Lovelace",
                "ada@frc.utn.edu.ar", "351-1234567", subjectId, List.of(items));
    }

    private static CreateRoomRequestItemDto item(Long commissionId) {
        return item(commissionId, null, List.of());
    }

    private static CreateRoomRequestItemDto item(Long commissionId, Integer currentClassroomId,
                                                 List<Integer> preferredClassroomIds) {
        return new CreateRoomRequestItemDto(commissionId, LocalDate.now().plusDays(7),
                LocalTime.of(10, 0), LocalTime.of(12, 0), 30, 35, 1, currentClassroomId,
                false, false, null, null, null, null, preferredClassroomIds);
    }

    private static CreateRoomRequestItemDto examUsersItem(Long commissionId, boolean requiresComputers,
                                                          Boolean requiresExamUsers) {
        return new CreateRoomRequestItemDto(commissionId, LocalDate.now().plusDays(7),
                LocalTime.of(10, 0), LocalTime.of(12, 0), 30, 35, 1, null,
                false, requiresComputers, requiresComputers ? 20 : null,
                requiresExamUsers, null, null, List.of());
    }

    private static CreateRoomRequestItemDto otherItem(Long commissionId, String observations) {
        return new CreateRoomRequestItemDto(commissionId, LocalDate.now().plusDays(7),
                LocalTime.of(10, 0), LocalTime.of(12, 0), 30, 35, 1, null,
                false, false, null, null, null, observations, List.of());
    }

    private static List<ClassroomResponseDto> classrooms(Integer... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new ClassroomResponseDto(id, "A" + id, 1, 40, true, 1, "Pabellón", 1, "Aula"))
                .toList();
    }
}
