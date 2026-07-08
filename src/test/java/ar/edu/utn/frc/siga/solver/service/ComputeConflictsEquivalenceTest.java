package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.service.impl.SolverServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que la adyacencia de conflictos del barrido optimizado es idéntica a la de
 * una implementación naive O(n²·d²) (todos los pares), usada acá como oráculo.
 */
class ComputeConflictsEquivalenceTest {

    private static final long SEED = 20260703L;

    private SolverServiceImpl service;
    private Method computeConflicts;

    @BeforeEach
    void setUp() throws Exception {
        service = new SolverServiceImpl(null, null, null);
        computeConflicts = SolverServiceImpl.class.getDeclaredMethod("computeConflicts", List.class);
        computeConflicts.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> optimized(List<SolverEvent> events) throws Exception {
        return (Map<String, Set<String>>) computeConflicts.invoke(service, events);
    }

    /** Adyacencia de referencia O(n²·d²), conservada como oráculo. */
    private Map<String, Set<String>> naive(List<SolverEvent> events) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (int i = 0; i < events.size(); i++) {
            for (int j = i + 1; j < events.size(); j++) {
                SolverEvent a = events.get(i);
                SolverEvent b = events.get(j);
                boolean timesOverlap = a.startTime().isBefore(b.endTime())
                        && b.startTime().isBefore(a.endTime());
                if (!timesOverlap) continue;
                if (b.occurrenceDates().stream().anyMatch(a.occurrenceDates()::contains)) {
                    adjacency.computeIfAbsent(a.planningId(), id -> new java.util.HashSet<>()).add(b.planningId());
                    adjacency.computeIfAbsent(b.planningId(), id -> new java.util.HashSet<>()).add(a.planningId());
                }
            }
        }
        return adjacency;
    }

    @Test
    void largeRandomInstance_sameConflictSetAsNaive() throws Exception {
        List<SolverEvent> events = randomEvents(250);
        Map<String, Set<String>> expected = naive(events);

        // Sanidad del escenario: tiene que haber conflictos reales que comparar.
        assertThat(expected).isNotEmpty();
        assertThat(optimized(events)).isEqualTo(expected);
    }

    @Test
    void mixedUniqueAndRecurring_sameConflictSetAsNaive() throws Exception {
        List<SolverEvent> events = new ArrayList<>(randomEvents(60));
        Random random = new Random(SEED + 1);
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.of(2026, 3, 2).plusDays(random.nextInt(120));
            LocalTime start = LocalTime.of(8 + random.nextInt(12), random.nextBoolean() ? 0 : 30);
            events.add(new SolverEvent("uni-" + i, null, 20 + random.nextInt(60),
                    start, start.plusMinutes(90), Set.of(date)));
        }
        assertThat(optimized(events)).isEqualTo(naive(events));
    }

    @Test
    void emptyInput_noConflicts() throws Exception {
        assertThat(optimized(List.of())).isEmpty();
    }

    @Test
    void sameDateOverlappingEvents_singleCanonicalPair() throws Exception {
        LocalDate date = LocalDate.of(2026, 3, 2);
        SolverEvent a = new SolverEvent("A", null, 30, LocalTime.of(8, 0), LocalTime.of(9, 30), Set.of(date));
        SolverEvent b = new SolverEvent("B", null, 30, LocalTime.of(8, 0), LocalTime.of(9, 30), Set.of(date));
        assertThat(optimized(List.of(a, b)))
                .isEqualTo(Map.of("A", Set.of("B"), "B", Set.of("A")));
    }

    private List<SolverEvent> randomEvents(int count) {
        Random random = new Random(SEED);
        LocalDate semesterStart = LocalDate.of(2026, 3, 2);
        List<SolverEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            DayOfWeek dow = DayOfWeek.of(1 + random.nextInt(6));
            LocalTime start = LocalTime.of(8 + random.nextInt(12), random.nextBoolean() ? 0 : 30);
            int durationMinutes = 60 + 30 * random.nextInt(4);
            LocalDate from = semesterStart.plusWeeks(random.nextInt(4));
            LocalDate to = from.plusWeeks(8 + random.nextInt(10));
            events.add(new SolverEvent("rec-" + i, null, 20 + random.nextInt(60),
                    start, start.plusMinutes(durationMinutes), weeklyDates(dow, from, to)));
        }
        return events;
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
}
