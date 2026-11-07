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
import ar.edu.utn.frc.siga.events.dto.response.SysacadRecurringEventRefDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.NotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.BaseRoom;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.LoadResult;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.ScheduleKey;
import ar.edu.utn.frc.siga.sysacad.internal.write.DirectChangeHandler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    @Mock
    private SysacadScheduleLoader scheduleLoader;
    @Mock
    private DirectChangeHandler directChangeHandler;

    private static final int COMMISSION_NUMBER = 7;

    private AllocationSyncService service;

    @BeforeEach
    void setUp() {
        service = serviceIn(SysacadWriteMode.OFF);
        lenient().when(scheduleLoader.load(anyList())).thenReturn(new LoadResult(Set.of(), List.of(), List.of()));
    }

    private AllocationSyncService serviceIn(SysacadWriteMode mode) {
        SysacadProperties properties = new SysacadProperties();
        properties.setWrite(SysacadProperties.Write.of(mode));
        return new AllocationSyncService(commissionService, subjectCommissionService,
                academicEventService, classroomService, allocationService, syncStateService, scheduleLoader,
                directChangeHandler, properties);
    }

    @Test
    @DisplayName("view: expone la vista ASIGNACIONES")
    void viewReturnsAsignaciones() {
        assertThat(service.view()).isEqualTo(SysacadView.ASIGNACIONES);
    }

    private CommissionResponseDto commission(Long id, String courseCode, int year) {
        return new CommissionResponseDto(id, courseCode,
                new AcademicPeriodResponseDto(year, TermType.ANUAL.getSemester(), null, null), null);
    }

    private SubjectCommissionResponseDto link(Long subjectId, Long commissionId, int enrolled) {
        return new SubjectCommissionResponseDto(subjectId, null, commissionId, null, enrolled, true, true);
    }

    private ClassroomResponseDto classroom(Long id) {
        return new ClassroomResponseDto(id, 200, 40, 5L, "Edificio", 1L, "Normal", null, true);
    }

    private SysacadAllocationDto row(String courseCode, Integer subjectCode, Integer semester,
            Integer roomNumber, Integer buildingCode) {
        return new SysacadAllocationDto(
                courseCode, subjectCode, 1, 2024, DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, LocalTime.of(9, 30),
                COMMISSION_NUMBER, semester, roomNumber, buildingCode);
    }

    private SysacadRecurringEventRefDto eventRef(Long eventId, Long subjectId, Long commissionId,
            TermType termType, int year) {
        return new SysacadRecurringEventRefDto(eventId, subjectId, commissionId, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                termType.startDate(year), termType.endDate(year), true);
    }

    @ParameterizedTest
    @CsvSource({"999, 24", "999, 2", "999, 6", "0, 5"})
    @DisplayName("sync: aula centinela (999 o 0, cualquier edificio) → no resuelve aula ni propone AllocationItem, pero registra el vínculo con eventos")
    void syncSkipsSentinelRoom(int roomNumber, int buildingCode) {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, roomNumber, buildingCode)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(academicEventService.findSysacadRecurringEvents())
                .thenReturn(List.of(eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));

        service.sync(catalogReader);

        verify(classroomService, never()).findByRoomNumberAndBuildingCode(any(), any());
        verify(allocationService).syncFromSysacad(List.of(), Set.of());
        // The previous allocation of that event is kept: the sync has no deactivation or release path.
        verifyNoMoreInteractions(allocationService);
        verify(syncStateService).recordSuccess(SysacadView.ASIGNACIONES, 0);
        List<SysacadScheduleLoader.ResolvedScheduleRow> rows = loadedRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().eventIds()).containsExactly(100L);
        assertThat(rows.getFirst().skipReason()).isNull();
        assertThat(rows.getFirst().row().roomNumber()).isEqualTo(roomNumber);
    }

    private List<SysacadScheduleLoader.ResolvedScheduleRow> loadedRows() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysacadScheduleLoader.ResolvedScheduleRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleLoader).load(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("sync: no se resuelve una comisión vigente para el curso → saltea la fila con WARN")
    void syncSkipsRowWhenCommissionUnresolved() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("999", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("999"))
                .thenThrow(ResourceNotFoundException.of("Commission", "999"));

        service.sync(catalogReader);

        verify(subjectCommissionService, never()).findByCommissionAndSubjectCode(any(), any(), any(), any());
        verify(classroomService, never()).findByRoomNumberAndBuildingCode(any(), any());
        verify(allocationService).syncFromSysacad(List.of(), Set.of());
    }

    @Test
    @DisplayName("sync: no se resuelve el aula por (roomNumber, buildingCode) → saltea la fila con WARN")
    void syncSkipsRowWhenClassroomUnresolved() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.empty());

        service.sync(catalogReader);

        verify(allocationService).syncFromSysacad(List.of(), Set.of());
    }

    @Test
    @DisplayName("sync: el índice de eventos sync-owned se pide una sola vez por corrida")
    void syncFetchesRecurringEventIndexOnce() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents())
                .thenReturn(List.of(eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));
        when(allocationService.syncFromSysacad(anyList(), any())).thenReturn(1);

        service.sync(catalogReader);

        verify(academicEventService, times(1)).findSysacadRecurringEvents();
    }

    @Test
    @DisplayName("sync: el aula resuelve pero ninguna clave del índice matchea la fila → saltea la fila con WARN")
    void syncSkipsRowWhenEventNotFound() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of());

        Logger logger = (Logger) LoggerFactory.getLogger(AllocationSyncService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.sync(catalogReader);
        } finally {
            logger.detachAppender(appender);
        }

        boolean warned = appender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("No se encontró el evento ya creado por EVENTOS")
                        && event.getFormattedMessage().contains("101"));
        assertThat(warned).as("debe loguear WARN cuando la fila no tiene evento en el índice").isTrue();
        verify(allocationService).syncFromSysacad(List.of(), Set.of());
    }

    @Test
    @DisplayName("sync: fila resuelta por completo → arma un AllocationItem(Event, classroomId) y hace una sola llamada a syncFromSysacad")
    void syncBuildsItemAndDelegatesOnce() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents())
                .thenReturn(List.of(eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));
        when(allocationService.syncFromSysacad(anyList(), any())).thenReturn(1);

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService, times(1)).syncFromSysacad(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(new AllocationItem(new AllocationTarget.Event(100L), 50L));
        verify(syncStateService).recordSuccess(SysacadView.ASIGNACIONES, 1);
    }

    @Test
    @DisplayName("sync: HorarioCuatrimestre=0 (\"ambos cuatrimestres\") → resuelve un item por cuatrimestre desde el índice")
    void syncResolvesBothSemestersFromIndex() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 0, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of(
                eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026),
                eventRef(101L, 9L, 1L, TermType.SEGUNDO_CUATRIMESTRE, 2026)));

        service.sync(catalogReader);

        verify(academicEventService, times(1)).findSysacadRecurringEvents();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService).syncFromSysacad(captor.capture(), any());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                new AllocationItem(new AllocationTarget.Event(100L), 50L),
                new AllocationItem(new AllocationTarget.Event(101L), 50L));
    }

    @Test
    @DisplayName("sync: dos filas con el mismo evento y aulas distintas → conserva la primera aula, saltea la otra "
            + "con WARN y no aborta la vista")
    void syncKeepsFirstRoomWhenSameEventHasTwoRooms() {
        SysacadAllocationDto rowA = row("101", 55, 1, 200, 5);
        SysacadAllocationDto rowB = row("101", 55, 1, 201, 6);
        SysacadAllocationDto otherEvent = new SysacadAllocationDto("101", 55, 1, 2024, DayOfWeek.TUESDAY, LocalTime.of(8, 0),
                90, LocalTime.of(9, 30), COMMISSION_NUMBER, 1, 201, 6);
        when(catalogReader.findAllocations()).thenReturn(List.of(rowA, rowB, otherEvent));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(classroomService.findByRoomNumberAndBuildingCode(201, 6)).thenReturn(Optional.of(classroom(51L)));
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of(
                eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026),
                new SysacadRecurringEventRefDto(101L, 9L, 1L, DayOfWeek.TUESDAY, LocalTime.of(8, 0),
                        TermType.PRIMER_CUATRIMESTRE.startDate(2026), TermType.PRIMER_CUATRIMESTRE.endDate(2026), true)));

        Logger logger = (Logger) LoggerFactory.getLogger(AllocationSyncService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.sync(catalogReader);
        } finally {
            logger.detachAppender(appender);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService).syncFromSysacad(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(
                new AllocationItem(new AllocationTarget.Event(100L), 50L),
                new AllocationItem(new AllocationTarget.Event(101L), 51L));
        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("otra aula"));
        verify(syncStateService).recordSuccess(eq(SysacadView.ASIGNACIONES), anyInt());
    }

    @Test
    @DisplayName("sync: eventos duplicados en el índice (misma clave) no rompen la corrida y usan el primero")
    void syncToleratesDuplicateEventsInIndex() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of(
                eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026),
                eventRef(150L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService).syncFromSysacad(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(new AllocationItem(new AllocationTarget.Event(100L), 50L));
    }

    @Test
    @DisplayName("sync: filas idénticas del origen (mismo curso, materia, aula, edificio) → un solo AllocationItem, no lo aborta")
    void syncDeduplicatesIdenticalRows() {
        SysacadAllocationDto duplicated = row("101", 55, 1, 200, 5);
        when(catalogReader.findAllocations()).thenReturn(List.of(duplicated, duplicated));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents())
                .thenReturn(List.of(eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));

        service.sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService).syncFromSysacad(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(new AllocationItem(new AllocationTarget.Event(100L), 50L));
    }

    @Test
    @DisplayName("sync: dos filas comparten (edificio, aula, dia, hora, cuatrimestre) con curso/materia distintos → "
            + "WARN con el detalle del grupo, sin deduplicar ni abortar (supuestos §3)")
    void syncWarnsOverlapDetailWithoutDeduplicating() {
        SysacadAllocationDto rowA = row("101", 55, 1, 200, 5);
        SysacadAllocationDto rowB = row("102", 66, 1, 200, 5);
        when(catalogReader.findAllocations()).thenReturn(List.of(rowA, rowB));

        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(commissionService.findActiveByCourseCode("102")).thenReturn(commission(2L, "102", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(2L, 66, 1, 2024)).thenReturn(link(10L, 2L, 25));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of(
                eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026),
                eventRef(200L, 10L, 2L, TermType.PRIMER_CUATRIMESTRE, 2026)));

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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService).syncFromSysacad(captor.capture(), any());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("sync: una franja con una sola fila no genera aviso de solapamiento")
    void syncDoesNotWarnOverlapForSingleRowSlot() {
        SysacadAllocationDto rowA = row("101", 55, 1, 200, 5);
        SysacadAllocationDto otherSlot = new SysacadAllocationDto("102", 66, 1, 2024, DayOfWeek.MONDAY,
                LocalTime.of(10, 0), 90, LocalTime.of(11, 30), COMMISSION_NUMBER, 1, 200, 5);
        when(catalogReader.findAllocations()).thenReturn(List.of(rowA, otherSlot));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(commissionService.findActiveByCourseCode("102")).thenReturn(commission(2L, "102", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(2L, 66, 1, 2024)).thenReturn(link(10L, 2L, 25));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of(
                eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026),
                new SysacadRecurringEventRefDto(200L, 10L, 2L, DayOfWeek.MONDAY, LocalTime.of(10, 0),
                        TermType.PRIMER_CUATRIMESTRE.startDate(2026), TermType.PRIMER_CUATRIMESTRE.endDate(2026), true)));

        Logger logger = (Logger) LoggerFactory.getLogger(AllocationSyncService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.sync(catalogReader);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains("Solapamiento"));
        verify(allocationService).syncFromSysacad(anyList(), any());
    }

    @Test
    @DisplayName("sync: si falla, registra el error y propaga la excepción, sin llamar a syncFromSysacad")
    void syncRecordsFailureAndRethrows() {
        when(catalogReader.findAllocations()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync(catalogReader)).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(eq(SysacadView.ASIGNACIONES), startsWith("INTERNAL_ERROR"));
        verify(allocationService, never()).syncFromSysacad(any(), any());
    }

    @Test
    @DisplayName("sync: comisión sin resolver → registra la fila con COMMISSION_NOT_RESOLVED, año en curso y sin eventos")
    void syncRegistersScheduleWhenCommissionUnresolved() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("999", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("999"))
                .thenThrow(ResourceNotFoundException.of("Commission", "999"));

        service.sync(catalogReader);

        List<SysacadScheduleLoader.ResolvedScheduleRow> rows = loadedRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().skipReason()).isEqualTo(NotSendableReason.COMMISSION_NOT_RESOLVED);
        assertThat(rows.getFirst().year()).isEqualTo(LocalDate.now().getYear());
        assertThat(rows.getFirst().eventIds()).isEmpty();
        verify(allocationService).syncFromSysacad(List.of(), Set.of());
    }

    @Test
    @DisplayName("sync: aula desconocida → ROOM_UNKNOWN, conserva el vínculo con el evento y no propone AllocationItem")
    void syncKeepsEventLinkWhenRoomUnknown() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.empty());
        when(academicEventService.findSysacadRecurringEvents())
                .thenReturn(List.of(eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));

        service.sync(catalogReader);

        List<SysacadScheduleLoader.ResolvedScheduleRow> rows = loadedRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().skipReason()).isEqualTo(NotSendableReason.ROOM_UNKNOWN);
        assertThat(rows.getFirst().eventIds()).containsExactly(100L);
        assertThat(rows.getFirst().year()).isEqualTo(2026);
        verify(allocationService).syncFromSysacad(List.of(), Set.of());
    }

    @Test
    @DisplayName("sync: evento inexistente → EVENT_NOT_FOUND y sin eventos vinculados")
    void syncMarksEventNotFound() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of());

        service.sync(catalogReader);

        List<SysacadScheduleLoader.ResolvedScheduleRow> rows = loadedRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().skipReason()).isEqualTo(NotSendableReason.EVENT_NOT_FOUND);
        assertThat(rows.getFirst().eventIds()).isEmpty();
    }

    @Test
    @DisplayName("sync: mismo evento con otra aula → la fila salteada lleva EVENT_ROOM_CONFLICT y la primera queda limpia")
    void syncMarksEventRoomConflictOnSkippedRow() {
        when(catalogReader.findAllocations())
                .thenReturn(List.of(row("101", 55, 1, 200, 5), row("101", 55, 1, 201, 6)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(classroomService.findByRoomNumberAndBuildingCode(201, 6)).thenReturn(Optional.of(classroom(51L)));
        when(academicEventService.findSysacadRecurringEvents())
                .thenReturn(List.of(eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));

        service.sync(catalogReader);

        List<SysacadScheduleLoader.ResolvedScheduleRow> rows = loadedRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).skipReason()).isNull();
        assertThat(rows.get(1).skipReason()).isEqualTo(NotSendableReason.EVENT_ROOM_CONFLICT);
        assertThat(rows.get(1).row().roomNumber()).isEqualTo(201);
        assertThat(rows.get(1).eventIds()).containsExactly(100L);
    }

    @Test
    @DisplayName("sync: fila sin commissionNumber queda fuera de la carga de horarios pero sigue proponiendo su AllocationItem")
    void syncLeavesRowWithoutCommissionNumberOutOfSchedules() {
        SysacadAllocationDto noNumber = new SysacadAllocationDto("101", 55, 1, 2024, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), 90, LocalTime.of(9, 30), null, 1, 200, 5);
        when(catalogReader.findAllocations()).thenReturn(List.of(noNumber));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents())
                .thenReturn(List.of(eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));

        service.sync(catalogReader);

        assertThat(loadedRows()).isEmpty();
        verify(allocationService).syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(100L), 50L)), Set.of(100L));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL,2024,55", "1,NULL,55", "1,2024,NULL"})
    @DisplayName("sync: fila sin especialidad, plan o materia queda fuera de la carga: esas columnas son NOT NULL")
    void syncLeavesRowWithoutKeyFieldsOutOfSchedules(Integer specialty, Integer plan, Integer subject) {
        SysacadAllocationDto incomplete = new SysacadAllocationDto("101", subject, specialty, plan, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), 90, LocalTime.of(9, 30), COMMISSION_NUMBER, 1, 200, 5);
        when(catalogReader.findAllocations()).thenReturn(List.of(incomplete));
        when(commissionService.findActiveByCourseCode("101")).thenThrow(new ResourceNotFoundException("sin comisión"));

        service.sync(catalogReader);

        assertThat(loadedRows()).isEmpty();
    }

    @Test
    @DisplayName("sync: fila 999 sin evento en SIGA queda con EVENT_NOT_FOUND, sin proponer asignación")
    void syncMarksSentinelRowWithoutEventAsNotFound() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 999, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of());

        service.sync(catalogReader);

        List<SysacadScheduleLoader.ResolvedScheduleRow> rows = loadedRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().eventIds()).isEmpty();
        assertThat(rows.getFirst().skipReason()).isEqualTo(NotSendableReason.EVENT_NOT_FOUND);
        verify(allocationService).syncFromSysacad(List.of(), Set.of());
    }

    @Test
    @DisplayName("sync: fila con semester inválido queda fuera de la carga de horarios")
    void syncLeavesRowWithInvalidSemesterOutOfSchedules() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 5, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));

        service.sync(catalogReader);

        assertThat(loadedRows()).isEmpty();
    }

    @Test
    @DisplayName("sync: fila anual (semester 0) → un solo registro con los dos eventos en el vínculo")
    void syncLinksBothEventsForAnnualRow() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 0, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of(
                eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026),
                eventRef(101L, 9L, 1L, TermType.SEGUNDO_CUATRIMESTRE, 2026)));

        service.sync(catalogReader);

        List<SysacadScheduleLoader.ResolvedScheduleRow> rows = loadedRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().eventIds()).containsExactly(100L, 101L);
        assertThat(rows.getFirst().skipReason()).isNull();
    }

    @Test
    @DisplayName("sync: el loader de horarios se invoca antes de syncFromSysacad")
    void syncLoadsSchedulesBeforeSyncingAllocations() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(academicEventService.findSysacadRecurringEvents())
                .thenReturn(List.of(eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));

        service.sync(catalogReader);

        InOrder order = inOrder(scheduleLoader, allocationService);
        order.verify(scheduleLoader).load(anyList());
        order.verify(allocationService).syncFromSysacad(anyList(), any());
    }

    // ---------- aula base con la escritura en ON ----------

    private static final ScheduleKey KEY = new ScheduleKey(2026, 1, 2024, 55, COMMISSION_NUMBER,
            DayOfWeek.MONDAY.getValue(), 1);

    private AllocationSyncService writeOnWithBase(Integer baseBuilding, Integer baseRoom) {
        AllocationSyncService onService = serviceIn(SysacadWriteMode.ON);
        when(scheduleLoader.findBaseRooms()).thenReturn(baseBuilding == null
                ? Map.of() : Map.of(KEY, new BaseRoom(baseBuilding, baseRoom)));
        return onService;
    }

    private void stubResolvedEvent() {
        when(commissionService.findActiveByCourseCode("101")).thenReturn(commission(1L, "101", 2026));
        when(subjectCommissionService.findByCommissionAndSubjectCode(1L, 55, 1, 2024)).thenReturn(link(9L, 1L, 30));
        when(academicEventService.findSysacadRecurringEvents())
                .thenReturn(List.of(eventRef(100L, 9L, 1L, TermType.PRIMER_CUATRIMESTRE, 2026)));
    }

    @Test
    @DisplayName("ON: evento nuevo con aula base se crea con la base aunque la fila muestre un 229 puntual")
    void writeOnProposesBaseRoomEvenIfRowShowsOtherRoom() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 229, 5)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(228, 5)).thenReturn(Optional.of(classroom(50L)));
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.of(classroom(51L)));

        writeOnWithBase(5, 228).sync(catalogReader);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationService).syncFromSysacad(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(new AllocationItem(new AllocationTarget.Event(100L), 50L));
        assertThat(loadedRows().getFirst().skipReason()).isNull();
    }

    @Test
    @DisplayName("ON: fila 999 con aula base real propone la base")
    void writeOnSentinelRowWithRealBaseProposesBase() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 999, 24)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(228, 5)).thenReturn(Optional.of(classroom(50L)));

        writeOnWithBase(5, 228).sync(catalogReader);

        verify(classroomService, never()).findByRoomNumberAndBuildingCode(eq(999), any());
        verify(allocationService).syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(100L), 50L)), Set.of());
    }

    @Test
    @DisplayName("ON: aula base 999 no propone nada aunque la fila muestre un aula real")
    void writeOnSentinelBaseProposesNothing() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 229, 5)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.of(classroom(51L)));

        writeOnWithBase(5, 999).sync(catalogReader);

        verify(allocationService).syncFromSysacad(List.of(), Set.of());
        assertThat(loadedRows().getFirst().skipReason()).isNull();
        assertThat(loadedRows().getFirst().eventIds()).containsExactly(100L);
    }

    @Test
    @DisplayName("ON: horario sin aula base propone el aula de la fila")
    void writeOnScheduleWithoutBaseProposesRow() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 229, 5)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.of(classroom(51L)));

        writeOnWithBase(null, null).sync(catalogReader);

        verify(allocationService).syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(100L), 51L)), Set.of());
    }

    @Test
    @DisplayName("ON: aula base que no resuelve da ROOM_UNKNOWN y no propone asignación")
    void writeOnUnresolvedBaseIsRoomUnknown() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 229, 5)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(228, 5)).thenReturn(Optional.empty());

        writeOnWithBase(5, 228).sync(catalogReader);

        assertThat(loadedRows().getFirst().skipReason()).isEqualTo(NotSendableReason.ROOM_UNKNOWN);
        verify(allocationService).syncFromSysacad(List.of(), Set.of());
    }

    @Test
    @DisplayName("ON: aula de la fila que no resuelve conserva ROOM_UNKNOWN aunque la base resuelva")
    void writeOnUnresolvedRowRoomKeepsRoomUnknownEvenIfBaseResolves() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 229, 5)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(228, 5)).thenReturn(Optional.of(classroom(50L)));
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.empty());

        writeOnWithBase(5, 228).sync(catalogReader);

        assertThat(loadedRows().getFirst().skipReason()).isEqualTo(NotSendableReason.ROOM_UNKNOWN);
    }

    @Test
    @DisplayName("OFF: propone el aula de la fila y no consulta las aulas base")
    void writeOffProposesRowAndSkipsBaseLookup() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 229, 5)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.of(classroom(51L)));

        service.sync(catalogReader);

        verify(scheduleLoader, never()).findBaseRooms();
        verify(allocationService).syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(100L), 51L)), Set.of());
    }

    @Test
    @DisplayName("sync: pasa a syncFromSysacad los eventIdsToUpdate que devuelve el loader")
    void syncPassesEventIdsToUpdateFromLoadResult() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(scheduleLoader.load(anyList())).thenReturn(new LoadResult(Set.of(100L), List.of(4L), List.of()));

        service.sync(catalogReader);

        verify(allocationService).syncFromSysacad(
                List.of(new AllocationItem(new AllocationTarget.Event(100L), 50L)), Set.of(100L));
        verify(directChangeHandler).handle(4L);
    }

    @Test
    @DisplayName("sync: registra los cambios directos simulados que devuelve el loader")
    void syncRecordsDryRunChanges() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(scheduleLoader.load(anyList())).thenReturn(new LoadResult(Set.of(), List.of(),
                List.of(new SysacadScheduleLoader.DryRunChange(8L, 5, 200, 5, 229))));

        service.sync(catalogReader);

        verify(directChangeHandler).recordDryRun(8L, 5, 200, 5, 229);
        verify(directChangeHandler, never()).handle(any());
    }

    @Test
    @DisplayName("sync: un fallo del handler en un horario no corta los demás ni el sync")
    void syncKeepsGoingWhenHandlerFails() {
        when(catalogReader.findAllocations()).thenReturn(List.of(row("101", 55, 1, 200, 5)));
        stubResolvedEvent();
        when(classroomService.findByRoomNumberAndBuildingCode(200, 5)).thenReturn(Optional.of(classroom(50L)));
        when(scheduleLoader.load(anyList())).thenReturn(new LoadResult(Set.of(), List.of(4L, 5L), List.of()));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(directChangeHandler).handle(4L);

        service.sync(catalogReader);

        verify(directChangeHandler).handle(5L);
    }
}
