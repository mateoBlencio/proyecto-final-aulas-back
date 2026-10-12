package ar.edu.utn.frc.siga.roomrequest.direct;

import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventDraftDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.direct.DirectEventDraftValidator.ResolvedSchedule;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DirectEventDraftValidator")
class DirectEventDraftValidatorTest {

    private final UserService userService = Mockito.mock(UserService.class);
    private final AcademicReferenceValidator academicReferences = Mockito.mock(AcademicReferenceValidator.class);
    private final ClassScheduleService classSchedule = Mockito.mock(ClassScheduleService.class);
    private final DirectEventDraftValidator validator =
            new DirectEventDraftValidator(userService, academicReferences, classSchedule);

    @ParameterizedTest
    @EnumSource(value = RoomRequestType.class, names = {"ONE_TIME_ROOM_CHANGE", "REGULAR_ROOM_CHANGE"})
    @DisplayName("los cambios de aula dan 400 antes de cualquier otra validación y sin llegar a DirectEventKinds.of")
    void rejectsRoomChangeTypes(RoomRequestType type) {
        DirectEventDraftDto draft = new DirectEventDraftDto(type, 1L, 2L, LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(11, 0), 30, 1, null, true, 20, null, null, null);

        assertThatThrownBy(() -> validator.validate(draft, "aux@utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class)
                .satisfies(ex -> assertThat(((InvalidRoomRequestException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        Mockito.verifyNoInteractions(userService, academicReferences, classSchedule);
    }

    private static final String SUBSECRETARIA = "sub@utn.edu.ar";
    private static final String AUXILIAR = "aux@utn.edu.ar";
    private static final LocalDate FUTURE = LocalDate.now().plusDays(3);

    private static DirectEventDraftDto draft(RoomRequestType type, Long commissionId, LocalTime start, LocalTime end,
            Boolean requiresComputers, String description) {
        Integer computerCount = Boolean.TRUE.equals(requiresComputers) ? 20 : null;
        return new DirectEventDraftDto(type, 1L, commissionId, FUTURE, start, end, 30, 1, null,
                requiresComputers, computerCount, null, null, description);
    }

    private static DirectEventDraftDto timed(RoomRequestType type, Long commissionId) {
        return draft(type, commissionId, LocalTime.of(9, 0), LocalTime.of(11, 0), true, "detalle");
    }

    private void assertBadRequest(DirectEventDraftDto draft, String actor) {
        assertThatThrownBy(() -> validator.validate(draft, actor))
                .isInstanceOf(InvalidRoomRequestException.class)
                .satisfies(ex -> assertThat(((InvalidRoomRequestException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("parcial en clase sin comisión: 400 por la regla real de AcademicReferenceValidator")
    void partialInClassWithoutCommission_isRejected() {
        DirectEventDraftValidator withRealReferences = new DirectEventDraftValidator(userService,
                new AcademicReferenceValidator(Mockito.mock(SubjectService.class), Mockito.mock(CommissionService.class),
                        Mockito.mock(SubjectCommissionService.class)),
                classSchedule);
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);
        DirectEventDraftDto draft = draft(RoomRequestType.PARTIAL_EXAM_IN_CLASS, null, null, null, true, null);

        assertThatThrownBy(() -> withRealReferences.validate(draft, SUBSECRETARIA))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("commissionId");
        Mockito.verifyNoInteractions(classSchedule);
    }

    @ParameterizedTest
    @EnumSource(value = RoomRequestType.class, names = {"FINAL_EXAM", "CONFERENCE"})
    @DisplayName("FINAL_EXAM y CONFERENCE con comisión: 400 y no se resuelve el horario")
    void commissionNotAllowed_isRejected(RoomRequestType type) {
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);

        assertBadRequest(timed(type, 7L), SUBSECRETARIA);
        Mockito.verifyNoInteractions(classSchedule);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("OTHER con description nula, vacía o en blanco: 400")
    void otherWithoutDescription_isRejected(String description) {
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);

        assertBadRequest(draft(RoomRequestType.OTHER, null, LocalTime.of(9, 0), LocalTime.of(11, 0), true, description),
                SUBSECRETARIA);
    }

    @Test
    @DisplayName("hoy con hora de inicio ya pasada: 400")
    void startTimeAlreadyPassed_isRejected() {
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);
        DirectEventDraftDto draft = new DirectEventDraftDto(RoomRequestType.CONFERENCE, null, null, LocalDate.now(),
                LocalTime.MIN, LocalTime.MIN.plusHours(1), 30, 1, null, true, 20, null, null, "charla");

        assertBadRequest(draft, SUBSECRETARIA);
    }

    @Test
    @DisplayName("fin igual al inicio: 400")
    void endNotAfterStart_isRejected() {
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);

        assertBadRequest(draft(RoomRequestType.CONFERENCE, null, LocalTime.of(9, 0), LocalTime.of(9, 0), true, "charla"),
                SUBSECRETARIA);
    }

    @Test
    @DisplayName("parcial en clase que manda startTime/endTime: 400 sin consultar el cursado")
    void partialInClassWithTimesInBody_isRejected() {
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);

        assertBadRequest(timed(RoomRequestType.PARTIAL_EXAM_IN_CLASS, 7L), SUBSECRETARIA);
        Mockito.verifyNoInteractions(classSchedule);
    }

    @Test
    @DisplayName("parcial en clase en una fecha sin cursado: propaga el 400 de requireClassDate")
    void partialInClassOnNonClassDate_propagatesRejection() {
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);
        when(classSchedule.requireClassDate(1L, 7L, FUTURE))
                .thenThrow(new InvalidRoomRequestException("La comisión 7 no tiene clase el " + FUTURE + "."));

        assertBadRequest(draft(RoomRequestType.PARTIAL_EXAM_IN_CLASS, 7L, null, null, true, null), SUBSECRETARIA);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    @DisplayName("auxiliar con requiresComputers false o nulo: 400")
    void auxiliarWithoutComputers_isRejected(Boolean requiresComputers) {
        when(userService.hasRole(AUXILIAR, SystemRole.SUBSECRETARIA)).thenReturn(false);

        assertBadRequest(draft(RoomRequestType.CONFERENCE, null, LocalTime.of(9, 0), LocalTime.of(11, 0),
                requiresComputers, "charla"), AUXILIAR);
    }

    @Test
    @DisplayName("auxiliar con requiresComputers true pasa; subsecretaría sin computadoras también")
    void computersRule_onlyBindsTheAuxiliar() {
        when(userService.hasRole(AUXILIAR, SystemRole.SUBSECRETARIA)).thenReturn(false);
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);
        DirectEventDraftDto withPcs = draft(RoomRequestType.CONFERENCE, null, LocalTime.of(9, 0), LocalTime.of(11, 0),
                true, "charla");
        DirectEventDraftDto withoutPcs = draft(RoomRequestType.CONFERENCE, null, LocalTime.of(9, 0), LocalTime.of(11, 0),
                false, "charla");

        assertThatCode(() -> validator.validate(withPcs, AUXILIAR)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(withoutPcs, SUBSECRETARIA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("caso feliz fuera de clase: devuelve el horario del body")
    void validDraft_returnsBodySchedule() {
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);

        ResolvedSchedule schedule = validator.validate(
                draft(RoomRequestType.FINAL_EXAM, null, LocalTime.of(14, 30), LocalTime.of(17, 0), false, null),
                SUBSECRETARIA);

        assertThat(schedule.startTime()).isEqualTo(LocalTime.of(14, 30));
        assertThat(schedule.duration()).isEqualTo(Duration.ofMinutes(150));
        assertThat(schedule.endTime()).isEqualTo(LocalTime.of(17, 0));
        verify(academicReferences).requireSubject(1L);
        Mockito.verifyNoInteractions(classSchedule);
    }

    @Test
    @DisplayName("caso feliz parcial en clase: devuelve el horario del cursado, no el del body")
    void validPartialInClass_returnsClassSchedule() {
        when(userService.hasRole(SUBSECRETARIA, SystemRole.SUBSECRETARIA)).thenReturn(true);
        when(classSchedule.requireClassDate(any(), any(), any())).thenReturn(
                new ClassSlot(5L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(20, 0)));

        ResolvedSchedule schedule = validator.validate(
                draft(RoomRequestType.PARTIAL_EXAM_IN_CLASS, 7L, null, null, false, null), SUBSECRETARIA);

        assertThat(schedule.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(schedule.duration()).isEqualTo(Duration.ofHours(2));
        verify(classSchedule).requireClassDate(1L, 7L, FUTURE);
        verify(academicReferences).requireCommissionOfSubject(1L, 7L);
    }
}
