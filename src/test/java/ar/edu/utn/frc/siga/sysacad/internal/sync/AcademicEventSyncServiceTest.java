package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.command.SyncRecurringEventCommand;
import ar.edu.utn.frc.siga.events.service.command.UpsertRecurringEventResult;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAcademicEventDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicEventSyncService (thin syncer)")
class AcademicEventSyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private CommissionService commissionService;
    @Mock
    private SubjectCommissionService subjectCommissionService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private SysacadSyncStateService syncStateService;
    @Captor
    private ArgumentCaptor<List<SyncRecurringEventCommand>> commandsCaptor;

    private AcademicEventSyncService service;

    @BeforeEach
    void setUp() {
        service = new AcademicEventSyncService(
                commissionService, subjectCommissionService, academicEventService, syncStateService);
    }

    @Test
    @DisplayName("view: expone la vista EVENTOS")
    void viewReturnsEventos() {
        assertThat(service.view()).isEqualTo(SysacadView.EVENTOS);
    }

    private CommissionResponseDto commission(Long id, String courseCode, int year) {
        return new CommissionResponseDto(id, courseCode,
                new AcademicPeriodResponseDto(year, TermType.ANUAL.getSemester(), null, null));
    }

    private SubjectCommissionResponseDto link(Long subjectId, Long commissionId, int enrolled) {
        return new SubjectCommissionResponseDto(subjectId, commissionId, null, enrolled);
    }

    private UpsertRecurringEventResult created(long eventId) {
        return new UpsertRecurringEventResult(eventId, true, false);
    }

    @Test
    @DisplayName("sync: arma los commands y llama a syncRecurringEvents una sola vez, sin importar cuántas filas")
    void syncCallsBulkOnce() {
        SysacadAcademicEventDto monday = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 1);
        SysacadAcademicEventDto thursday = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.THURSDAY, LocalTime.of(8, 0), 90, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(monday, thursday));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(academicEventService.syncRecurringEvents(anyList()))
                .thenReturn(List.of(created(100L), created(101L)));

        service.sync(catalogReader);

        verify(academicEventService, times(1)).syncRecurringEvents(commandsCaptor.capture());
        assertThat(commandsCaptor.getValue()).hasSize(2);
        verify(commissionService, times(1)).findActiveByCourseCode("101");
        verify(subjectCommissionService, times(1)).findByCommissionAndSubjectCode(1L, 55);
    }

    @Test
    @DisplayName("sync: HorarioCuatrimestre=1 → un solo command, con las fechas del 1er cuatrimestre")
    void syncMapsFirstSemesterToSingleCommand() {
        SysacadAcademicEventDto row = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(row));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(academicEventService.syncRecurringEvents(anyList())).thenReturn(List.of(created(100L)));

        service.sync(catalogReader);

        verify(academicEventService).syncRecurringEvents(commandsCaptor.capture());
        assertThat(commandsCaptor.getValue()).hasSize(1);
        SyncRecurringEventCommand cmd = commandsCaptor.getValue().getFirst();
        assertThat(cmd.subjectId()).isEqualTo(9L);
        assertThat(cmd.commissionId()).isEqualTo(1L);
        assertThat(cmd.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(cmd.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(cmd.durationMinutes()).isEqualTo(90);
        assertThat(cmd.enrolled()).isEqualTo(30);
        assertThat(cmd.startDate()).isEqualTo(TermType.PRIMER_CUATRIMESTRE.startDate(2026));
        assertThat(cmd.endDate()).isEqualTo(TermType.PRIMER_CUATRIMESTRE.endDate(2026));

        verify(academicEventService).markRecurringEventsAbsent(Set.of(100L));
        verify(syncStateService).recordSuccess(SysacadView.EVENTOS, 1);
    }

    @Test
    @DisplayName("sync: HorarioCuatrimestre=2 → un solo command, con las fechas del 2do cuatrimestre")
    void syncMapsSecondSemesterToSingleCommand() {
        SysacadAcademicEventDto row = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 2);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(row));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(academicEventService.syncRecurringEvents(anyList())).thenReturn(List.of(created(101L)));

        service.sync(catalogReader);

        verify(academicEventService).syncRecurringEvents(commandsCaptor.capture());
        SyncRecurringEventCommand cmd = commandsCaptor.getValue().getFirst();
        assertThat(cmd.startDate()).isEqualTo(TermType.SEGUNDO_CUATRIMESTRE.startDate(2026));
        assertThat(cmd.endDate()).isEqualTo(TermType.SEGUNDO_CUATRIMESTRE.endDate(2026));
    }

    @Test
    @DisplayName("sync: HorarioCuatrimestre=0 (\"ambos cuatrimestres\") → emite DOS commands, uno por cuatrimestre")
    void syncMapsBothSemestersToTwoCommands() {
        SysacadAcademicEventDto row = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 0);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(row));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(academicEventService.syncRecurringEvents(anyList()))
                .thenReturn(List.of(created(100L), created(101L)));

        service.sync(catalogReader);

        verify(academicEventService).syncRecurringEvents(commandsCaptor.capture());
        assertThat(commandsCaptor.getValue()).extracting(SyncRecurringEventCommand::startDate)
                .containsExactlyInAnyOrder(
                        TermType.PRIMER_CUATRIMESTRE.startDate(2026), TermType.SEGUNDO_CUATRIMESTRE.startDate(2026));
        verify(academicEventService).markRecurringEventsAbsent(Set.of(100L, 101L));
        verify(syncStateService).recordSuccess(SysacadView.EVENTOS, 2);
    }

    @Test
    @DisplayName("sync: no se resuelve una comisión vigente para el curso → saltea la fila con WARN, command no emitido")
    void syncSkipsRowWhenCommissionUnresolved() {
        SysacadAcademicEventDto row = new SysacadAcademicEventDto(
                "999", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(row));
        when(commissionService.findActiveByCourseCode("999"))
                .thenThrow(ResourceNotFoundException.of("Commission", "999"));

        service.sync(catalogReader);

        verify(academicEventService).syncRecurringEvents(commandsCaptor.capture());
        assertThat(commandsCaptor.getValue()).isEmpty();
        verify(subjectCommissionService, never()).findByCommissionAndSubjectCode(any(), any());
        verify(academicEventService).markRecurringEventsAbsent(Set.of());
        verify(syncStateService).recordSuccess(SysacadView.EVENTOS, 0);
    }

    @Test
    @DisplayName("sync: no se resuelve el link materia-comisión → saltea la fila con WARN, command no emitido")
    void syncSkipsRowWhenLinkUnresolved() {
        SysacadAcademicEventDto row = new SysacadAcademicEventDto(
                "101", 77, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(row));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 77))
                .thenThrow(ResourceNotFoundException.of("SubjectCommission", "1-77"));

        service.sync(catalogReader);

        verify(academicEventService).syncRecurringEvents(commandsCaptor.capture());
        assertThat(commandsCaptor.getValue()).isEmpty();
        verify(academicEventService).markRecurringEventsAbsent(Set.of());
    }

    @Test
    @DisplayName("sync: DURACION nula → saltea la fila con WARN, command no emitido")
    void syncSkipsRowWhenDurationMissing() {
        SysacadAcademicEventDto row = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), null, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(row));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));

        service.sync(catalogReader);

        verify(academicEventService).syncRecurringEvents(commandsCaptor.capture());
        assertThat(commandsCaptor.getValue()).isEmpty();
        verify(academicEventService).markRecurringEventsAbsent(Set.of());
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción, sin llamar a markRecurringEventsAbsent")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findAcademicEvents()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(SysacadView.EVENTOS, "SysAcad caído");
        verify(academicEventService, never()).markRecurringEventsAbsent(anyCollection());
    }
}
