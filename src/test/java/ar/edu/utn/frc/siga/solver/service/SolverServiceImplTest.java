package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.siga.solver.dto.request.AllocationParametersDto;
import ar.edu.utn.frc.siga.solver.dto.request.AllocationRequestDto;
import ar.edu.utn.frc.siga.solver.dto.request.EventRequestDto;
import ar.edu.utn.frc.siga.solver.dto.request.PinnedAssignmentDto;
import ar.edu.utn.frc.siga.solver.config.SolverProperties;
import ar.edu.utn.frc.siga.solver.exception.InvalidAllocationRequestException;
import ar.edu.utn.frc.siga.solver.mapper.AllocationRequestMapper;
import ar.edu.utn.frc.siga.solver.mapper.AllocationResponseMapper;
import ar.edu.utn.frc.siga.solver.model.ConflictPair;
import ar.edu.utn.frc.siga.solver.optimization.ScheduleSolution;
import ar.edu.utn.frc.siga.solver.optimization.SolverEvent;
import ar.edu.utn.frc.siga.solver.service.impl.SolverServiceImpl;
import ai.timefold.solver.core.api.solver.SolverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SolverServiceImplTest {

    @Mock AllocationRequestMapper requestMapper;
    @Mock AllocationResponseMapper responseMapper;
    @Mock SolverManager<ScheduleSolution> solverManager;
    @Mock SolverProperties solverProperties;
    @InjectMocks SolverServiceImpl service;

    private Method timesOverlap;
    private Method computeConflicts;
    private Method validateBusinessRules;

    @BeforeEach
    void setUp() throws Exception {
        timesOverlap = SolverServiceImpl.class.getDeclaredMethod("timesOverlap", SolverEvent.class, SolverEvent.class);
        timesOverlap.setAccessible(true);

        computeConflicts = SolverServiceImpl.class.getDeclaredMethod("computeConflicts", List.class);
        computeConflicts.setAccessible(true);

        validateBusinessRules = SolverServiceImpl.class.getDeclaredMethod(
                "validateBusinessRules", AllocationRequestDto.class, AllocationParametersDto.class);
        validateBusinessRules.setAccessible(true);
    }

    // ─── timesOverlap ───────────────────────────────────────────────────────

    private boolean overlap(int startHourA, int startMinA, int durA, int startHourB, int startMinB, int durB) throws Exception {
        SolverEvent a = uniqueEvent("a", LocalTime.of(startHourA, startMinA), durA);
        SolverEvent b = uniqueEvent("b", LocalTime.of(startHourB, startMinB), durB);
        return (boolean) timesOverlap.invoke(service, a, b);
    }

    private SolverEvent uniqueEvent(String id, LocalTime start, int dur) {
        LocalTime end = start.plusMinutes(dur);
        return new SolverEvent(id, 30, start, end, Set.of(LocalDate.of(2024, 1, 1)));
    }

    @Test
    void upTo001_overlap_partialOverlap() throws Exception {
        assertThat(overlap(8, 0, 90, 9, 0, 90)).isTrue();
    }

    @Test
    void upTo002_noOverlap_adjacent() throws Exception {
        assertThat(overlap(8, 0, 90, 9, 30, 90)).isFalse();
    }

    @Test
    void upTo003_noOverlap_gap() throws Exception {
        assertThat(overlap(8, 0, 90, 9, 31, 90)).isFalse();
    }

    @Test
    void upTo004_overlap_BinsideA() throws Exception {
        assertThat(overlap(8, 0, 180, 9, 0, 60)).isTrue();
    }

    @Test
    void upTo005_overlap_AinsideB() throws Exception {
        assertThat(overlap(9, 0, 60, 8, 0, 180)).isTrue();
    }

    @Test
    void upTo006_overlap_identical() throws Exception {
        assertThat(overlap(8, 0, 90, 8, 0, 90)).isTrue();
    }

    @Test
    void upTo007_noOverlap_eveningGap() throws Exception {
        assertThat(overlap(20, 40, 135, 18, 15, 135)).isFalse();
    }

    @Test
    void upTo008_noOverlap_eveningAdjacentGap() throws Exception {
        assertThat(overlap(18, 15, 135, 20, 40, 135)).isFalse();
    }

    // ─── computeConflicts ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Set<ConflictPair> conflicts(List<SolverEvent> events) throws Exception {
        return (Set<ConflictPair>) computeConflicts.invoke(service, events);
    }

    private Set<LocalDate> weeklyDates(DayOfWeek dow, LocalDate from, LocalDate to) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        LocalDate current = from.with(TemporalAdjusters.nextOrSame(dow));
        while (!current.isAfter(to)) {
            dates.add(current);
            current = current.plusWeeks(1);
        }
        return dates;
    }

    private SolverEvent recurring(String id, DayOfWeek dow, LocalTime start, int dur,
                                  LocalDate from, LocalDate to) {
        return new SolverEvent(id, 30, start, start.plusMinutes(dur), weeklyDates(dow, from, to));
    }

    private SolverEvent unique(String id, LocalTime start, int dur, LocalDate date) {
        return new SolverEvent(id, 30, start, start.plusMinutes(dur), Set.of(date));
    }

    @Test
    void upCc001_twoRecurring_sameSlot_overlappingDates_oneConflict() throws Exception {
        SolverEvent a = recurring("A", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 6, 30));
        SolverEvent b = recurring("B", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 5, 1), LocalDate.of(2024, 7, 31));
        assertThat(conflicts(List.of(a, b))).hasSize(1);
    }

    @Test
    void upCc002_twoRecurring_sameSlot_disjointDates_noConflict() throws Exception {
        SolverEvent a = recurring("A", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 6, 28));
        SolverEvent b = recurring("B", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 8, 5), LocalDate.of(2024, 11, 29));
        assertThat(conflicts(List.of(a, b))).isEmpty();
    }

    @Test
    void upCc003_twoRecurring_sameDay_adjacentTimes_noConflict() throws Exception {
        SolverEvent a = recurring("A", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 6, 28));
        SolverEvent b = recurring("B", DayOfWeek.MONDAY, LocalTime.of(9, 30), 90,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 6, 28));
        assertThat(conflicts(List.of(a, b))).isEmpty();
    }

    @Test
    void upCc004_recurring_plus_unique_thursday_inRange_oneConflict() throws Exception {
        SolverEvent rec = recurring("REC", DayOfWeek.THURSDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 3, 7), LocalDate.of(2024, 11, 28));
        SolverEvent uni = unique("UNI", LocalTime.of(8, 0), 90, LocalDate.of(2024, 5, 9));
        assertThat(conflicts(List.of(rec, uni))).hasSize(1);
    }

    @Test
    void upCc005_recurring_plus_unique_thursday_outsideRange_noConflict() throws Exception {
        SolverEvent rec = recurring("REC", DayOfWeek.THURSDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 3, 7), LocalDate.of(2024, 6, 27));
        SolverEvent uni = unique("UNI", LocalTime.of(8, 0), 90, LocalDate.of(2024, 8, 15));
        assertThat(conflicts(List.of(rec, uni))).isEmpty();
    }

    @Test
    void upCc006_twoUnique_sameDate_sameTime_oneConflict() throws Exception {
        SolverEvent a = unique("A", LocalTime.of(8, 0), 90, LocalDate.of(2024, 7, 23));
        SolverEvent b = unique("B", LocalTime.of(8, 0), 90, LocalDate.of(2024, 7, 23));
        assertThat(conflicts(List.of(a, b))).hasSize(1);
    }

    @Test
    void upCc007_twoUnique_sameDate_differentTimes_noConflict() throws Exception {
        SolverEvent a = unique("A", LocalTime.of(8, 0), 90, LocalDate.of(2024, 7, 23));
        SolverEvent b = unique("B", LocalTime.of(14, 0), 90, LocalDate.of(2024, 7, 23));
        assertThat(conflicts(List.of(a, b))).isEmpty();
    }

    @Test
    void upCc008_twoUnique_differentDates_sameTime_noConflict() throws Exception {
        SolverEvent a = unique("A", LocalTime.of(8, 0), 90, LocalDate.of(2024, 7, 23));
        SolverEvent b = unique("B", LocalTime.of(8, 0), 90, LocalDate.of(2024, 7, 24));
        assertThat(conflicts(List.of(a, b))).isEmpty();
    }

    @Test
    void upCc009_threeEvents_partialConflicts() throws Exception {
        LocalDate start = LocalDate.of(2024, 3, 4);
        LocalDate end = LocalDate.of(2024, 3, 31);
        SolverEvent a = recurring("A", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90, start, end);
        SolverEvent b = recurring("B", DayOfWeek.MONDAY, LocalTime.of(9, 0), 90, start, end);
        SolverEvent c = recurring("C", DayOfWeek.MONDAY, LocalTime.of(10, 0), 90, start, end);
        Set<ConflictPair> result = conflicts(List.of(a, b, c));
        assertThat(result).hasSize(2);
        assertThat(result).contains(new ConflictPair("A", "B"), new ConflictPair("B", "C"));
        assertThat(result).doesNotContain(new ConflictPair("A", "C"));
    }

    @Test
    void upCc010_singleEvent_noConflicts() throws Exception {
        SolverEvent a = recurring("A", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 6, 30));
        assertThat(conflicts(List.of(a))).isEmpty();
    }

    // ─── validateBusinessRules ───────────────────────────────────────────────

    private AllocationRequestDto requestWith(List<EventRequestDto> events, List<ClassroomResponseDTO> classrooms) {
        AllocationRequestDto dto = new AllocationRequestDto();
        dto.setEvents(events);
        dto.setClassrooms(classrooms);
        return dto;
    }

    private EventRequestDto eventDto(String id) {
        EventRequestDto dto = new EventRequestDto();
        dto.setId(id);
        dto.setType(EventRequestDto.EventType.RECURRING);
        dto.setEnrolled(30);
        dto.setStartTime(LocalTime.of(8, 0));
        dto.setDurationMinutes(90);
        dto.setDayOfWeek(DayOfWeek.MONDAY);
        dto.setStartDate(LocalDate.of(2024, 3, 4));
        dto.setEndDate(LocalDate.of(2024, 6, 30));
        return dto;
    }

    private ClassroomResponseDTO classroomDto(Integer id) {
        return ClassroomResponseDTO.builder()
                .id(id).roomNumber("Room " + id).capacity(80)
                .floor(1).available(true).buildingName("Building A")
                .build();
    }

    private void validateExpectingException(AllocationRequestDto request, AllocationParametersDto params)
            throws Exception {
        try {
            validateBusinessRules.invoke(service, request, params);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof InvalidAllocationRequestException cause) throw cause;
            throw e;
        }
    }

    private boolean validateExpectingException_safe(AllocationRequestDto request, AllocationParametersDto params) {
        try {
            validateBusinessRules.invoke(service, request, params);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void upVl001_valid_noException() {
        AllocationRequestDto request = requestWith(List.of(eventDto("ev-1")), List.of(classroomDto(1)));
        assertThat(validateExpectingException_safe(request, new AllocationParametersDto())).isTrue();
    }

    @Test
    void upVl002_duplicateEventId_throws() {
        AllocationRequestDto request = requestWith(List.of(eventDto("dup"), eventDto("dup")), List.of(classroomDto(1)));
        assertThatThrownBy(() -> validateExpectingException(request, new AllocationParametersDto()))
                .isInstanceOf(InvalidAllocationRequestException.class).hasMessageContaining("dup");
    }

    @Test
    void upVl003_duplicateClassroomId_throws() {
        AllocationRequestDto request = requestWith(List.of(eventDto("ev-1")), List.of(classroomDto(99), classroomDto(99)));
        assertThatThrownBy(() -> validateExpectingException(request, new AllocationParametersDto()))
                .isInstanceOf(InvalidAllocationRequestException.class).hasMessageContaining("99");
    }

    @Test
    void upVl004_pinnedEventNotFound_throws() {
        AllocationRequestDto request = requestWith(List.of(eventDto("ev-real")), List.of(classroomDto(1)));
        AllocationParametersDto params = new AllocationParametersDto();
        PinnedAssignmentDto pin = new PinnedAssignmentDto();
        pin.setEventId("ev-ghost"); pin.setClassroomId(1);
        params.setPinnedAssignments(List.of(pin));
        assertThatThrownBy(() -> validateExpectingException(request, params))
                .isInstanceOf(InvalidAllocationRequestException.class);
    }

    @Test
    void upVl005_pinnedClassroomNotFound_throws() {
        AllocationRequestDto request = requestWith(List.of(eventDto("ev-1")), List.of(classroomDto(1)));
        AllocationParametersDto params = new AllocationParametersDto();
        PinnedAssignmentDto pin = new PinnedAssignmentDto();
        pin.setEventId("ev-1"); pin.setClassroomId(999);
        params.setPinnedAssignments(List.of(pin));
        assertThatThrownBy(() -> validateExpectingException(request, params))
                .isInstanceOf(InvalidAllocationRequestException.class);
    }

    @Test
    void upVl006_sameEventInTwoPins_throws() {
        AllocationRequestDto request = requestWith(List.of(eventDto("ev-1")), List.of(classroomDto(1), classroomDto(2)));
        AllocationParametersDto params = new AllocationParametersDto();
        PinnedAssignmentDto pin1 = new PinnedAssignmentDto(); pin1.setEventId("ev-1"); pin1.setClassroomId(1);
        PinnedAssignmentDto pin2 = new PinnedAssignmentDto(); pin2.setEventId("ev-1"); pin2.setClassroomId(2);
        params.setPinnedAssignments(List.of(pin1, pin2));
        assertThatThrownBy(() -> validateExpectingException(request, params))
                .isInstanceOf(InvalidAllocationRequestException.class);
    }

    @Test
    void upVl007_pinnedAndExcluded_throws() {
        AllocationRequestDto request = requestWith(List.of(eventDto("ev-1")), List.of(classroomDto(1)));
        AllocationParametersDto params = new AllocationParametersDto();
        PinnedAssignmentDto pin = new PinnedAssignmentDto(); pin.setEventId("ev-1"); pin.setClassroomId(1);
        params.setPinnedAssignments(List.of(pin));
        params.setExcludedClassroomIds(List.of(1));
        assertThatThrownBy(() -> validateExpectingException(request, params))
                .isInstanceOf(InvalidAllocationRequestException.class);
    }

    @Test
    void upVl008_validPinPlusOtherExclusion_noException() {
        AllocationRequestDto request = requestWith(List.of(eventDto("ev-1")), List.of(classroomDto(1), classroomDto(2)));
        AllocationParametersDto params = new AllocationParametersDto();
        PinnedAssignmentDto pin = new PinnedAssignmentDto(); pin.setEventId("ev-1"); pin.setClassroomId(1);
        params.setPinnedAssignments(List.of(pin));
        params.setExcludedClassroomIds(List.of(2));
        assertThat(validateExpectingException_safe(request, params)).isTrue();
    }
}
