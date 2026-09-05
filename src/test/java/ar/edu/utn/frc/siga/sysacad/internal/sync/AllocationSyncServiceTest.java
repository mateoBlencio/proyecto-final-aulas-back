package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationSyncService (thin syncer)")
class AllocationSyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private CommissionService commissionService;
    @Mock
    private SubjectCommissionService subjectCommissionService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AllocationService allocationService;
    @Mock
    private SysacadSyncStateService syncStateService;

    private AllocationSyncService service;

    @BeforeEach
    void setUp() {
        service = new AllocationSyncService(commissionService, subjectCommissionService,
                academicEventService, classroomService, allocationService, syncStateService);
    }

    @Test
    @DisplayName("view: expone la vista ASIGNACIONES")
    void viewReturnsAsignaciones() {
        assertThat(service.view()).isEqualTo(SysacadView.ASIGNACIONES);
    }

    private CommissionResponseDto commission(Long id, String courseCode, int year) {
        return new CommissionResponseDto(id, courseCode,
                new AcademicPeriodResponseDto(year, TermType.ANUAL.getSemester(), null, null));
    }

    private SubjectCommissionResponseDto link(Long subjectId, Long commissionId, int enrolled) {
        return new SubjectCommissionResponseDto(subjectId, commissionId, null, enrolled);
    }

    private ClassroomResponseDto classroom(Long id) {
        return new ClassroomResponseDto(id, 200, 40, 5L, "Edificio", 1L, "Normal");
    }

    private SysacadAllocationDto row(String courseCode, Integer subjectCode, Integer semester,
            Integer roomNumber, Integer buildingCode) {
        return new SysacadAllocationDto(
                courseCode, subjectCode, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, semester, roomNumber, buildingCode);
    }

    @ParameterizedTest
    @CsvSource({"999, 24", "999, 2", "999, 6", "0, 5"})
    @DisplayName("sync: aula centinela (999 o 0, cualquier edificio) → saltea la fila sin resolver comisión, materia ni aula")
    void syncSkipsSentinelRoom(int roomNumber, int buildingCode) {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, roomNumber, buildingCode)));

        service.sync(catalogReader);

        verify(commissionService, never()).findActiveByCourseCode(any());
        verify(classroomService, never()).findByRoomNumberAndBuildingCode(any(), any());
        verify(academicEventService, never()).findRecurringEventId(any(), any(), any(), any(), any(), any());
        verify(allocationService).syncFromSysacad(List.of());
        verify(syncStateService).recordSuccess(SysacadView.ASIGNACIONES, 0);
    }

    @Test
    @DisplayName("sync: no se resuelve una comisión vigente para el curso → saltea la fila con WARN")
    void syncSkipsRowWhenCommissionUnresolved() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("999", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("999"))
                .thenThrow(ResourceNotFoundException.of("Commission", "999"));

        service.sync(catalogReader);

        verify(subjectCommissionService, never()).findByCommissionAndSubjectCode(any(), any());
        verify(classroomService, never()).findByRoomNumberAndBuildingCode(any(), any());
        verify(allocationService).syncFromSysacad(List.of());
    }

    @Test
    @DisplayName("sync: no se resuelve el aula por (roomNumber, buildingCode) → saltea la fila con WARN, no busca el evento")
    void syncSkipsRowWhenClassroomUnresolved() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.empty());

        service.sync(catalogReader);

        verify(academicEventService, never()).findRecurringEventId(any(), any(), any(), any(), any(), any());
        verify(allocationService).syncFromSysacad(List.of());
    }

    @Test
    @DisplayName("sync: el aula resuelve pero el evento no fue creado todavía por EVENTOS → saltea la fila con WARN, no crea el evento")
    void syncSkipsRowWhenEventNotFound() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findRecurringEventId(9L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                TermType.PRIMER_CUATRIMESTRE.startDate(2026), TermType.PRIMER_CUATRIMESTRE.endDate(2026)))
                .thenReturn(Optional.empty());

        service.sync(catalogReader);

        verify(allocationService).syncFromSysacad(List.of());
    }

    @Test
    @DisplayName("sync: fila resuelta por completo → arma un AllocationItem(Event, classroomId) y hace una sola llamada a syncFromSysacad")
    void syncBuildsItemAndDelegatesOnce() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findRecurringEventId(9L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                TermType.PRIMER_CUATRIMESTRE.startDate(2026), TermType.PRIMER_CUATRIMESTRE.endDate(2026)))
                .thenReturn(Optional.of(100L));
        when(allocationService.syncFromSysacad(anyList())).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService, times(1)).syncFromSysacad(captor.capture());
        assertThat(captor.getValue()).containsExactly(new AllocationItem(new AllocationTarget.Event(100L), 50L));
        verify(syncStateService).recordSuccess(SysacadView.ASIGNACIONES, 1);
    }

    @Test
    @DisplayName("sync: HorarioCuatrimestre=0 (\"ambos cuatrimestres\") → busca el evento dos veces, uno por cuatrimestre")
    void syncLooksUpTwiceWhenBothSemesters() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 0, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findRecurringEventId(9L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                TermType.PRIMER_CUATRIMESTRE.startDate(2026), TermType.PRIMER_CUATRIMESTRE.endDate(2026)))
                .thenReturn(Optional.of(100L));
        when(academicEventService.findRecurringEventId(9L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                TermType.SEGUNDO_CUATRIMESTRE.startDate(2026), TermType.SEGUNDO_CUATRIMESTRE.endDate(2026)))
                .thenReturn(Optional.of(101L));

        service.sync(catalogReader);

        verify(academicEventService, times(2)).findRecurringEventId(any(), any(), any(), any(), any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService).syncFromSysacad(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                new AllocationItem(new AllocationTarget.Event(100L), 50L),
                new AllocationItem(new AllocationTarget.Event(101L), 50L));
    }

    @Test
    @DisplayName("sync: dos filas comparten (edificio, aula, dia, hora, cuatrimestre) con curso/materia distintos → "
            + "WARN con el detalle del grupo, sin deduplicar ni abortar (supuestos §3)")
    void syncWarnsOverlapDetailWithoutDeduplicating() {
        SysacadAllocationDto rowA = row("101", 55, 1, 200, 5);
        SysacadAllocationDto rowB = row("102", 66, 1, 200, 5);
        when(catalogReader.findAllocations()).thenReturn(List.of(rowA, rowB));

        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55)).thenReturn(link(9L, 1L, 30));
        when(commissionService.findActiveByCourseCode("102")).thenReturn(commission(2L, "102", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(2L, 66)).thenReturn(link(10L, 2L, 25));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findRecurringEventId(9L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                TermType.PRIMER_CUATRIMESTRE.startDate(2026), TermType.PRIMER_CUATRIMESTRE.endDate(2026)))
                .thenReturn(Optional.of(100L));
        when(academicEventService.findRecurringEventId(10L, 2L, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                TermType.PRIMER_CUATRIMESTRE.startDate(2026), TermType.PRIMER_CUATRIMESTRE.endDate(2026)))
                .thenReturn(Optional.of(200L));

        Logger logger = (Logger) LoggerFactory.getLogger(AllocationSyncService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.sync(catalogReader);
        } finally {
            logger.detachAppender(appender);
        }

        boolean overlapWarned = appender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Solapamiento")
                        && event.getFormattedMessage().contains("101")
                        && event.getFormattedMessage().contains("102"));
        assertThat(overlapWarned).as("debe loguear WARN con el detalle (courseCode) de ambas filas del grupo").isTrue();

        // no deduplica: las dos filas siguen mandándose, aunque compartan aula/horario
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService).syncFromSysacad(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción, sin llamar a syncFromSysacad")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findAllocations()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(SysacadView.ASIGNACIONES, "SysAcad caído");
        verify(allocationService, never()).syncFromSysacad(any());
    }
}
