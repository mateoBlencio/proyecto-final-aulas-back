package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.audit.internal.AuditDescriptionProbe;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.command.SyncRecurringEventCommand;
import ar.edu.utn.frc.siga.events.service.command.UpsertRecurringEventResult;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAcademicEventDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.common.exception.SyncAbortedException;

import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadEmptyViewException;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.startsWith;
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
                commissionService, subjectCommissionService, academicEventService, syncStateService,
                new SysacadProperties());
    }

    @Test
    @DisplayName("view: expone la vista EVENTOS")
    void viewReturnsEventos() {
        assertThat(service.view()).isEqualTo(SysacadView.EVENTOS);
    }

    private CommissionResponseDto commission(Long id, String courseCode, int year) {
        return new CommissionResponseDto(id, courseCode,
                new AcademicPeriodResponseDto(year, TermType.ANUAL.getSemester(), null, null), null);
    }

    private SubjectCommissionResponseDto link(Long subjectId, Long commissionId, int enrolled) {
        return new SubjectCommissionResponseDto(subjectId, commissionId, null, enrolled, true);
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

        verify(academicEventService).markRecurringEventsAbsent(Set.of(100L), Set.of(), 50);
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
        verify(academicEventService).markRecurringEventsAbsent(Set.of(100L, 101L), Set.of(), 50);
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

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(SyncAbortedException.class);

        verify(subjectCommissionService, never()).findByCommissionAndSubjectCode(any(), any());
        verify(academicEventService, never()).syncRecurringEvents(anyList());
        verify(academicEventService, never()).markRecurringEventsAbsent(anyCollection(), anyCollection(), anyInt());
        verify(syncStateService).recordFailure(eq(SysacadView.EVENTOS), anyString());
        verify(syncStateService, never()).recordSuccess(any(), anyInt());
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

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(SyncAbortedException.class);

        verify(academicEventService, never()).syncRecurringEvents(anyList());
        verify(academicEventService, never()).markRecurringEventsAbsent(anyCollection(), anyCollection(), anyInt());
    }

    @Test
    @DisplayName("sync: DURACION nula → saltea la fila con WARN, command no emitido")
    void syncSkipsRowWhenDurationMissing() {
        SysacadAcademicEventDto row = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), null, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(row));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(SyncAbortedException.class);

        verify(academicEventService, never()).syncRecurringEvents(anyList());
        verify(academicEventService, never()).markRecurringEventsAbsent(anyCollection(), anyCollection(), anyInt());
    }

    @Test
    @DisplayName("sync: una fila con comisión resuelta pero sin link protege esa comisión de la baja por ausencia")
    void syncProtectsCommissionOfSkippedRowFromAbsentMarking() {
        SysacadAcademicEventDto unlinked = new SysacadAcademicEventDto(
                "101", 77, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 1);
        SysacadAcademicEventDto healthy = new SysacadAcademicEventDto(
                "202", 55, DayOfWeek.MONDAY, LocalTime.of(10, 0), 90, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(unlinked, healthy));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(commissionService.findActiveByCourseCode("202")).thenReturn(commission(2L, "202", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 77))
                .thenThrow(ResourceNotFoundException.of("SubjectCommission", "1-77"));
        when(subjectCommissionService.findByCommissionAndSubjectCode(2L, 55)).thenReturn(link(9L, 2L, 30));
        when(academicEventService.syncRecurringEvents(anyList())).thenReturn(List.of(created(100L)));

        service.sync(catalogReader);

        verify(academicEventService).markRecurringEventsAbsent(Set.of(100L), Set.of(1L), 50);
    }

    @Test
    @DisplayName("sync: DURACION nula o cuatrimestre inválido también protegen la comisión; una resuelta sana no")
    void syncProtectsCommissionOfRowsWithoutDurationOrTerm() {
        SysacadAcademicEventDto noDuration = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), null, 1);
        SysacadAcademicEventDto badTerm = new SysacadAcademicEventDto(
                "303", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 9);
        SysacadAcademicEventDto healthy = new SysacadAcademicEventDto(
                "202", 55, DayOfWeek.MONDAY, LocalTime.of(10, 0), 90, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(noDuration, badTerm, healthy));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(commissionService.findActiveByCourseCode("303")).thenReturn(commission(3L, "303", 2026));
        when(commissionService.findActiveByCourseCode("202")).thenReturn(commission(2L, "202", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(any(), eq(55)))
                .thenAnswer(invocation -> link(9L, invocation.getArgument(0), 30));
        when(academicEventService.syncRecurringEvents(anyList())).thenReturn(List.of(created(100L)));

        service.sync(catalogReader);

        verify(academicEventService).markRecurringEventsAbsent(Set.of(100L), Set.of(1L, 3L), 50);
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción, sin llamar a markRecurringEventsAbsent")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findAcademicEvents()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(eq(SysacadView.EVENTOS), startsWith("INTERNAL_ERROR"));
        verify(academicEventService, never()).markRecurringEventsAbsent(anyCollection(), anyCollection(), anyInt());
    }

    // ---------- audit description ----------

    private String syncAndReadDescription(List<UpsertRecurringEventResult> results, int absent) {
        SysacadAcademicEventDto row = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(row));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(academicEventService.syncRecurringEvents(anyList())).thenAnswer(inv -> {
            AuditDescriptionProbe.stamp();
            return results;
        });
        when(academicEventService.markRecurringEventsAbsent(anySet(), anySet(), eq(50))).thenReturn(absent);
        AuditDescriptionProbe probe = new AuditDescriptionProbe();

        probe.audited(service).sync(catalogReader);

        return probe.rewrittenDescription();
    }

    private UpsertRecurringEventResult updated(long eventId) {
        return new UpsertRecurringEventResult(eventId, false, true);
    }

    private UpsertRecurringEventResult unchanged(long eventId) {
        return new UpsertRecurringEventResult(eventId, false, false);
    }

    @Test
    @DisplayName("sync: 1 alta, 0 cambios, 2 bajas → los contadores salen de created/updated y de markRecurringEventsAbsent")
    void syncDescribesCreatedUpdatedAndAbsent() {
        String description = syncAndReadDescription(List.of(created(100L)), 2);

        assertThat(description).isEqualTo("Sincronización de eventos desde SysAcad: 1 alta, 0 cambios, 2 bajas");
    }

    @Test
    @DisplayName("sync: 0 altas, 1 cambio, 1 baja → singular en cambio y en baja")
    void syncDescribesSingularUpdateAndAbsent() {
        String description = syncAndReadDescription(List.of(updated(100L)), 1);

        assertThat(description).isEqualTo("Sincronización de eventos desde SysAcad: 0 altas, 1 cambio, 1 baja");
    }

    @Test
    @DisplayName("sync: los eventos sin cambios no suman a altas ni a cambios")
    void syncDescribesIgnoresUnchangedEvents() {
        String description = syncAndReadDescription(
                List.of(created(1L), created(2L), updated(3L), updated(4L), updated(5L), unchanged(6L), unchanged(7L)), 0);

        assertThat(description).isEqualTo("Sincronización de eventos desde SysAcad: 2 altas, 3 cambios, 0 bajas");
    }

    @Test
    @DisplayName("sync: el valor de retorno sigue siendo presentes más bajas, no cambia por la descripción")
    void syncReturnValueUnchangedByDescription() {
        SysacadAcademicEventDto row = new SysacadAcademicEventDto(
                "101", 55, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, 1);
        when(catalogReader.findAcademicEvents()).thenReturn(List.of(row));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(academicEventService.syncRecurringEvents(anyList())).thenReturn(List.of(created(1L), unchanged(2L)));
        when(academicEventService.markRecurringEventsAbsent(anySet(), anySet(), eq(50))).thenReturn(3);

        service.sync(catalogReader);

        verify(syncStateService).recordSuccess(SysacadView.EVENTOS, 5);
    }

    @Test
    @DisplayName("sync: con la vista vacía no llama al servicio de dominio y registra el fallo")
    void syncWithEmptyViewSkipsDomainServiceAndRecordsFailure() {
        when(catalogReader.findAcademicEvents()).thenReturn(List.of());

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(SysacadEmptyViewException.class);

        verify(academicEventService, never()).syncRecurringEvents(anyList());
        verify(academicEventService, never()).markRecurringEventsAbsent(anySet(), anySet(), eq(50));
        verify(syncStateService).recordFailure(eq(SysacadView.EVENTOS), anyString());
        verify(syncStateService, never()).recordSuccess(any(), anyInt());
    }
}
