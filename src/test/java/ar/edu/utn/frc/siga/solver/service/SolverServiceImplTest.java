package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.solver.config.SolverProperties;
import ar.edu.utn.frc.siga.solver.model.ScheduleSolution;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.service.impl.SolverServiceImpl;
import ai.timefold.solver.core.api.solver.SolverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SolverServiceImplTest {

    @Mock SolverManager<ScheduleSolution> solverManager;
    @Mock SolverProperties solverProperties;
    @Mock PreviewStore previewStore;
    @InjectMocks SolverServiceImpl service;

    private Method timesOverlap;
    private Method computeConflicts;

    @BeforeEach
    void setUp() throws Exception {
        timesOverlap = SolverServiceImpl.class.getDeclaredMethod("timesOverlap", SolverEvent.class, SolverEvent.class);
        timesOverlap.setAccessible(true);
        computeConflicts = SolverServiceImpl.class.getDeclaredMethod("computeConflicts", List.class);
        computeConflicts.setAccessible(true);
    }

    // ─── timesOverlap ───────────────────────────────────────────────────────

    private boolean overlap(int startHourA, int startMinA, int durA, int startHourB, int startMinB, int durB) throws Exception {
        SolverEvent a = uniqueEvent("a", LocalTime.of(startHourA, startMinA), durA);
        SolverEvent b = uniqueEvent("b", LocalTime.of(startHourB, startMinB), durB);
        return (boolean) timesOverlap.invoke(service, a, b);
    }

    private SolverEvent uniqueEvent(String id, LocalTime start, int dur) {
        return new SolverEvent(id, null, 30, start, start.plusMinutes(dur), Set.of(LocalDate.of(2024, 1, 1)));
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

    // ─── computeConflicts (adyacencia) ───────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> conflicts(List<SolverEvent> events) throws Exception {
        return (Map<String, Set<String>>) computeConflicts.invoke(service, events);
    }

    private int edges(Map<String, Set<String>> adjacency) {
        return adjacency.values().stream().mapToInt(Set::size).sum() / 2;
    }

    private boolean hasEdge(Map<String, Set<String>> adjacency, String a, String b) {
        return adjacency.getOrDefault(a, Set.of()).contains(b);
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

    private SolverEvent recurring(String id, DayOfWeek dow, LocalTime start, int dur, LocalDate from, LocalDate to) {
        return new SolverEvent(id, null, 30, start, start.plusMinutes(dur), weeklyDates(dow, from, to));
    }

    private SolverEvent unique(String id, LocalTime start, int dur, LocalDate date) {
        return new SolverEvent(id, null, 30, start, start.plusMinutes(dur), Set.of(date));
    }

    @Test
    void upCc001_twoRecurring_sameSlot_overlappingDates_oneConflict() throws Exception {
        SolverEvent a = recurring("A", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 6, 30));
        SolverEvent b = recurring("B", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 5, 1), LocalDate.of(2024, 7, 31));
        assertThat(edges(conflicts(List.of(a, b)))).isEqualTo(1);
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
        assertThat(edges(conflicts(List.of(rec, uni)))).isEqualTo(1);
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
        assertThat(edges(conflicts(List.of(a, b)))).isEqualTo(1);
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
        Map<String, Set<String>> result = conflicts(List.of(a, b, c));
        assertThat(edges(result)).isEqualTo(2);
        assertThat(hasEdge(result, "A", "B")).isTrue();
        assertThat(hasEdge(result, "B", "C")).isTrue();
        assertThat(hasEdge(result, "A", "C")).isFalse();
    }

    @Test
    void upCc010_singleEvent_noConflicts() throws Exception {
        SolverEvent a = recurring("A", DayOfWeek.MONDAY, LocalTime.of(8, 0), 90,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 6, 30));
        assertThat(conflicts(List.of(a))).isEmpty();
    }
}
