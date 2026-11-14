package ar.edu.utn.frc.siga.sysacad.internal.write.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.SysacadRecurringEventRefDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.service.OccurrenceRequestQuery;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSysacadRefDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NotifyOnly;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Send;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadWritePlanner")
class SysacadWritePlannerTest {

    private static final LocalDateTime NOW = LocalDate.of(2026, 3, 2).atTime(7, 0);
    private static final LocalDate D1 = LocalDate.of(2026, 3, 2);
    private static final LocalDate D2 = LocalDate.of(2026, 3, 9);
    private static final LocalDate D3 = LocalDate.of(2026, 3, 16);
    private static final LocalDate D4 = LocalDate.of(2026, 3, 23);

    @Mock
    private SysacadScheduleRepository scheduleRepository;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationService allocationService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private OccurrenceRequestQuery occurrenceRequestQuery;
    @Mock
    private HolidayService holidayService;

    private SysacadWritePlanner planner;

    @BeforeEach
    void setUp() {
        planner = new SysacadWritePlanner(scheduleRepository, academicEventService, occurrenceService,
                allocationService, classroomService, occurrenceRequestQuery, holidayService);
    }

    private static SysacadRecurringEventRefDto eventRef(long id, boolean enabled) {
        return new SysacadRecurringEventRefDto(id, 1L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31), enabled);
    }

    private static SysacadSchedule schedule(long id, Long event1, Long event2) {
        SysacadSchedule schedule = SysacadSchedule.builder()
                .id(id).knownBuilding(1).knownRoom(228).eventId1(event1).eventId2(event2)
                .status(SysacadScheduleStatus.SYNCED)
                .build();
        return schedule;
    }

    private static OccurrenceSlotDto slot(long occurrenceId, long eventId, LocalDate date, LocalTime start) {
        return new OccurrenceSlotDto(occurrenceId, eventId, date, start, start.plusMinutes(135),
                OccurrenceStatus.NEEDS_ROOM, 30);
    }

    private static OccurrenceSlotDto slot(long occurrenceId, long eventId, LocalDate date) {
        return slot(occurrenceId, eventId, date, LocalTime.of(8, 0));
    }

    private static ClassroomSysacadRefDto room(long classroomId) {
        return new ClassroomSysacadRefDto(classroomId, (int) classroomId, 1, true, true, true);
    }

    private void stubSchedules(SysacadSchedule... schedules) {
        when(scheduleRepository.findPlannable(SysacadScheduleStatus.REVIEW)).thenReturn(List.of(schedules));
    }

    private void stubEvents(SysacadRecurringEventRefDto... refs) {
        when(academicEventService.findSysacadRecurringEvents()).thenReturn(List.of(refs));
    }

    private void stubDownstream(Map<Long, Long> classroomByOccurrence) {
        when(allocationService.findClassroomIdsByOccurrences(anyCollection())).thenReturn(classroomByOccurrence);
        when(classroomService.findSysacadRefs(anyCollection()))
                .thenReturn(classroomByOccurrence.values().stream().distinct().map(SysacadWritePlannerTest::room)
                        .toList());
        when(occurrenceRequestQuery.findStates(anyCollection())).thenReturn(Map.of());
        when(holidayService.findBetween(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Filtra los horarios sin evento vigente en SysAcad y no consulta nada más")
    void skipsSchedulesWithoutCurrentEvent() {
        stubEvents(eventRef(10, true), eventRef(12, false));
        stubSchedules(schedule(1, 12L, null));

        assertThat(planner.plan(NOW)).isEmpty();

        verifyNoInteractions(occurrenceService, allocationService, classroomService, occurrenceRequestQuery,
                holidayService);
    }

    @Test
    @DisplayName("Un horario sin ningún evento enlazado a la lista de eventos tampoco consulta ocurrencias")
    void skipsSchedulesWhoseEventsAreUnknown() {
        stubEvents(eventRef(10, true));
        stubSchedules(schedule(1, 99L, null));

        assertThat(planner.plan(NOW)).isEmpty();

        verifyNoInteractions(occurrenceService, allocationService, classroomService, occurrenceRequestQuery,
                holidayService);
    }

    @Test
    @DisplayName("Sin horarios planificables devuelve lista vacía sin consultar ocurrencias, aulas, estados ni feriados")
    void emptyScheduleListQueriesNothingElse() {
        stubEvents(eventRef(10, true));
        stubSchedules();

        assertThat(planner.plan(NOW)).isEmpty();

        verifyNoInteractions(occurrenceService, allocationService, classroomService, occurrenceRequestQuery,
                holidayService);
    }

    @Test
    @DisplayName("Sin ocurrencias futuras devuelve lista vacía")
    void noSlotsReturnsEmpty() {
        stubEvents(eventRef(10, true));
        stubSchedules(schedule(1, 10L, null));
        when(occurrenceService.findNextSlotsByEvents(any(), any(LocalDate.class), any(), eq(2))).thenReturn(List.of());
        when(allocationService.findClassroomIdsByOccurrences(anyCollection())).thenReturn(Map.of());
        when(classroomService.findSysacadRefs(anyCollection())).thenReturn(List.of());
        when(occurrenceRequestQuery.findStates(anyCollection())).thenReturn(Map.of());
        when(holidayService.findBetween(any(), any())).thenReturn(List.of());

        assertThat(planner.plan(NOW)).isEmpty();
    }

    @Test
    @DisplayName("En un horario anual con un evento no vigente usa solo el vigente, y no lo pide ni lo mira")
    void annualScheduleUsesOnlyCurrentEvents() {
        stubEvents(eventRef(10, true), eventRef(12, false));
        stubSchedules(schedule(1, 10L, 12L));
        when(occurrenceService.findNextSlotsByEvents(any(), any(LocalDate.class), any(), eq(2))).thenReturn(List.of(
                slot(1, 12, D1), slot(2, 10, D2)));
        stubDownstream(Map.of(2L, 229L));

        List<PlannedSchedule> plan = planner.plan(NOW);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).decision()).isEqualTo(new Send(1, 229, 2L, D2, LocalTime.of(8, 0), LocalTime.of(10, 15),
                List.of(new RoomRef(1, 229))));
        verify(occurrenceService).findNextSlotsByEvents(eq(Set.of(10L)), eq(D1), any(), eq(2));
        verify(allocationService).findClassroomIdsByOccurrences(Set.of(2L));
    }

    @Test
    @DisplayName("Descarta las decisiones Unchanged y conserva las que tienen algo para mostrar")
    void discardsUnchanged() {
        stubEvents(eventRef(10, true), eventRef(11, true));
        stubSchedules(schedule(1, 10L, null), schedule(2, 11L, null));
        when(occurrenceService.findNextSlotsByEvents(any(), any(LocalDate.class), any(), eq(2))).thenReturn(List.of(
                slot(1, 10, D1), slot(2, 11, D1)));
        stubDownstream(Map.of(1L, 228L, 2L, 229L));

        List<PlannedSchedule> plan = planner.plan(NOW);

        assertThat(plan).extracting(p -> p.schedule().getId()).containsExactly(2L);
        assertThat(plan.get(0).decision()).isInstanceOf(Send.class);
    }

    @Test
    @DisplayName("Ordena por la fecha y hora de la clase más cercana")
    void ordersByNearestClass() {
        stubEvents(eventRef(10, true), eventRef(11, true), eventRef(12, true), eventRef(13, true));
        stubSchedules(schedule(1, 10L, null), schedule(2, 11L, null), schedule(3, 12L, null),
                schedule(4, 13L, null));
        when(occurrenceService.findNextSlotsByEvents(any(), any(LocalDate.class), any(), eq(2))).thenReturn(List.of(
                slot(1, 10, D3), slot(2, 11, D1, LocalTime.of(10, 0)), slot(3, 12, D2),
                slot(4, 13, D1, LocalTime.of(8, 0))));
        stubDownstream(Map.of(1L, 229L, 2L, 229L, 3L, 229L, 4L, 229L));

        List<PlannedSchedule> plan = planner.plan(NOW);

        assertThat(plan).extracting(p -> p.schedule().getId()).containsExactly(4L, 2L, 3L, 1L);
    }

    @Test
    @DisplayName("Consulta aulas, aulas de destino y estados solo de las próximas ocurrencias, una vez para todos los horarios")
    void queriesOnlyNextOccurrencesInOneBatch() {
        stubEvents(eventRef(10, true), eventRef(11, true), eventRef(12, true));
        stubSchedules(schedule(1, 10L, null), schedule(2, 11L, null), schedule(3, 12L, null));
        when(occurrenceService.findNextSlotsByEvents(any(), any(LocalDate.class), any(), eq(2))).thenReturn(List.of(
                slot(1, 10, D1), slot(2, 10, D2), slot(3, 10, D3),
                slot(4, 11, D2), slot(5, 11, D3),
                slot(6, 12, D4)));
        stubDownstream(Map.of(1L, 229L, 4L, 230L, 6L, 229L));

        planner.plan(NOW);

        verify(allocationService, times(1)).findClassroomIdsByOccurrences(Set.of(1L, 4L, 6L));
        verify(classroomService, times(1)).findSysacadRefs(Set.of(229L, 230L));
        verify(occurrenceRequestQuery, times(1)).findStates(Set.of(1L, 4L, 6L));
        verify(occurrenceService, times(1)).findNextSlotsByEvents(any(), any(LocalDate.class), any(), eq(2));
        verify(academicEventService, times(1)).findSysacadRecurringEvents();
        verify(holidayService, times(1)).findBetween(any(), any());
    }

    @Test
    @DisplayName("Pide 2 clases por evento desde hoy, excluyendo los feriados del año siguiente")
    void queriesNextTwoSlotsExcludingHolidaysOfNextYear() {
        stubEvents(eventRef(10, true));
        stubSchedules(schedule(1, 10L, null));
        when(occurrenceService.findNextSlotsByEvents(any(), any(LocalDate.class), any(), eq(2))).thenReturn(List.of(
                slot(1, 10, D1), slot(2, 10, D2)));
        stubDownstream(Map.of(1L, 229L));
        when(holidayService.findBetween(D1, D1.plusYears(1))).thenReturn(List.of(new HolidayResponseDto(D4, "Feriado")));

        planner.plan(NOW);

        verify(holidayService).findBetween(D1, D1.plusYears(1));
        verify(occurrenceService).findNextSlotsByEvents(Set.of(10L), D1, Set.of(D4), 2);
    }

    @Test
    @DisplayName("Un feriado en la fecha de la próxima clase hace que se tome la siguiente")
    void holidayMakesPlannerTakeTheFollowingClass() {
        stubEvents(eventRef(10, true));
        stubSchedules(schedule(1, 10L, null));
        when(occurrenceService.findNextSlotsByEvents(any(), any(LocalDate.class), any(), eq(2))).thenReturn(List.of(
                slot(1, 10, D1), slot(2, 10, D2)));
        stubDownstream(Map.of(2L, 229L));
        when(holidayService.findBetween(D1, D1.plusYears(1))).thenReturn(List.of(new HolidayResponseDto(D1, "Feriado")));

        List<PlannedSchedule> plan = planner.plan(NOW);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).decision()).isEqualTo(new Send(1, 229, 2L, D2, LocalTime.of(8, 0), LocalTime.of(10, 15),
                List.of(new RoomRef(1, 229))));
    }

    private static OccurrenceSlotDto mirrorSlot(long occurrenceId, long principalId, Integer roomSlot) {
        return new OccurrenceSlotDto(occurrenceId, 10L, D1, LocalTime.of(8, 0), LocalTime.of(10, 15),
                OccurrenceStatus.NEEDS_ROOM, 30, roomSlot, null, null, null, principalId);
    }

    @Test
    @DisplayName("Pide los espejos de las próximas, carga sus aulas en una sola llamada de refs y arma el conjunto por roomSlot")
    void loadsMirrorsAndBuildsRoomSetOrderedByRoomSlot() {
        stubEvents(eventRef(10, true));
        stubSchedules(schedule(1, 10L, null));
        when(occurrenceService.findNextSlotsByEvents(any(), any(LocalDate.class), any(), eq(2))).thenReturn(List.of(
                slot(1, 10, D1), slot(2, 10, D2)));
        when(allocationService.findClassroomIdsByOccurrences(Set.of(1L))).thenReturn(Map.of(1L, 228L));
        when(occurrenceService.findMirrors(Set.of(1L))).thenReturn(List.of(
                mirrorSlot(11, 1, 4), mirrorSlot(12, 1, 3), mirrorSlot(13, 1, 2)));
        when(allocationService.findClassroomIdsByOccurrences(Set.of(11L, 12L, 13L)))
                .thenReturn(Map.of(11L, 230L, 12L, 229L));
        when(classroomService.findSysacadRefs(Set.of(228L, 229L, 230L)))
                .thenReturn(List.of(room(228), room(229), room(230)));
        when(occurrenceRequestQuery.findStates(anyCollection())).thenReturn(Map.of());
        when(holidayService.findBetween(any(), any())).thenReturn(List.of());

        List<PlannedSchedule> plan = planner.plan(NOW);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).decision()).isEqualTo(new NotifyOnly(1L, D1,
                List.of(new RoomRef(1, 228), new RoomRef(1, 229), new RoomRef(1, 230))));
        verify(classroomService, times(1)).findSysacadRefs(anyCollection());
        verify(occurrenceService).findMirrors(Set.of(1L));
    }
}
