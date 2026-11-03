package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AllocationChangeNotifier: aviso por autogestión de cambios de aula")
class AllocationChangeNotifierTest {

    private SysacadCommissionMessageService messageService;
    private AcademicEventService eventService;
    private ClassroomService classroomService;
    private AllocationChangeNotifier notifier;

    @BeforeEach
    void setUp() {
        messageService = mock(SysacadCommissionMessageService.class);
        eventService = mock(AcademicEventService.class);
        classroomService = mock(ClassroomService.class);
        SysacadProperties properties = new SysacadProperties();
        properties.setAutogestion(new SysacadProperties.Autogestion(true, "TEST", 5, 581, 100, "U", 14));
        notifier = new AllocationChangeNotifier(messageService, eventService, classroomService, properties);
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(
                new ClassroomResponseDto(10L, 220, 40, 1L, "Edif", 1L, "Común", null, true, false, false, false),
                new ClassroomResponseDto(11L, 305, 40, 1L, "Edif", 1L, "Común", null, true, false, false, false)));
    }

    private static RecurringEventResponseDto event(long id, String courseCode, int specialty, int subject) {
        SpecialtyResponseDto specialtyDto = new SpecialtyResponseDto(specialty, "Sistemas", "Sist");
        SubjectResponseDto subjectDto = new SubjectResponseDto(1L, subject, "Materia", null,
                new StudyPlanResponseDto(1L, 2023, specialtyDto, true), true, null);
        CommissionResponseDto commission = new CommissionResponseDto(1L, courseCode,
                new AcademicPeriodResponseDto(1L, 2026, 0, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30),
                        null, null), null);
        return new RecurringEventResponseDto(id, EventType.RECURRING, 4, LocalTime.of(13, 15), 290,
                DayOfWeek.TUESDAY, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 30), subjectDto, commission);
    }

    private static ClassroomMovements movements(Change... changes) {
        return new ClassroomMovements("op", "admin", AllocationSource.MANUAL, null, Instant.now(), List.of(changes));
    }

    private static Change change(long eventId, Long from, Long to) {
        return new Change(1L, eventId, LocalDate.of(2026, 11, 10), LocalTime.of(13, 15), LocalTime.of(18, 5), from, to);
    }

    @Test
    @DisplayName("asignar, cambiar y liberar aula de la comisión habilitada envía un mensaje con los datos de la config")
    void sendsOneMessageForTargetCommission() {
        when(eventService.findByIds(any())).thenReturn(List.of(event(1, "TEST", 5, 581)));
        when(messageService.isTarget("TEST", 5, 581)).thenReturn(true);

        notifier.on(movements(change(1, null, 10L), change(1, 10L, 11L), change(1, 11L, null)));

        ArgumentCaptor<SendCommissionMessageRequest> sent = ArgumentCaptor.forClass(SendCommissionMessageRequest.class);
        verify(messageService).send(sent.capture());
        SendCommissionMessageRequest request = sent.getValue();
        assertThat(request.publicLegajo()).isEqualTo(100);
        assertThat(request.publicUserType()).isEqualTo("U");
        assertThat(request.untilDate()).isEqualTo(LocalDate.now().plusDays(14));
        assertThat(request.academicYear()).isEqualTo(2026);
        assertThat(request.courseCode()).isEqualTo("TEST");
        assertThat(request.note()).isEqualTo(
                "Cambio de aula: 10/11 13:15 aula 220; 10/11 13:15 aula 220>305; 10/11 13:15 sin aula");
    }

    @Test
    @DisplayName("cambios de otra comisión no envían nada")
    void ignoresOtherCommissions() {
        when(eventService.findByIds(any())).thenReturn(List.of(event(2, "1K1", 5, 519)));
        when(messageService.isTarget("1K1", 5, 519)).thenReturn(false);

        notifier.on(movements(change(2, null, 10L)));

        verify(messageService, never()).send(any());
    }

    @Test
    @DisplayName("si el envío falla, el listener no propaga la excepción")
    void swallowsSendFailures() {
        when(eventService.findByIds(any())).thenReturn(List.of(event(1, "TEST", 5, 581)));
        when(messageService.isTarget("TEST", 5, 581)).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(messageService).send(any());

        assertThatCode(() -> notifier.on(movements(change(1, null, 10L)))).doesNotThrowAnyException();
    }
}
