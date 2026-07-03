package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.solver.model.ConflictPair;
import ar.edu.utn.frc.siga.solver.optimization.SolverEvent;
import ar.edu.utn.frc.siga.solver.service.impl.SolverServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el computeConflicts optimizado (bucketing por fecha + barrido temporal)
 * produce exactamente el mismo conjunto de conflictos que la implementación naive
 * original (todos los pares, comparación de listas de fechas), usada acá como oráculo.
 */
class ComputeConflictsEquivalenceTest {

    private static final long SEED = 20260703L;

    private SolverServiceImpl service;
    private Method computeConflicts;

    @BeforeEach
    void setUp() throws Exception {
        service = new SolverServiceImpl(null, null, null, null);
        computeConflicts = SolverServiceImpl.class.getDeclaredMethod("computeConflicts", List.class);
        computeConflicts.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private Set<ConflictPair> optimized(List<SolverEvent> events) throws Exception {
        return (Set<ConflictPair>) computeConflicts.invoke(service, events);
    }

    /** Implementación original O(n²·d²), conservada como oráculo de referencia. */
    private Set<ConflictPair> naive(List<SolverEvent> events) {
        Set<ConflictPair> conflicts = new HashSet<>();
        for (int i = 0; i < events.size(); i++) {
            for (int j = i + 1; j < events.size(); j++) {
                SolverEvent a = events.get(i);
                SolverEvent b = events.get(j);
                boolean timesOverlap = a.startTime().isBefore(b.endTime())
                        && b.startTime().isBefore(a.endTime());
                if (!timesOverlap) continue;
                if (b.occurrenceDates().stream().anyMatch(a.occurrenceDates()::contains)) {
                    conflicts.add(new ConflictPair(a.planningId(), b.planningId()));
                }
            }
        }
        return conflicts;
    }

    @Test
    void largeRandomInstance_sameConflictSetAsNaive() throws Exception {
        List<SolverEvent> events = randomEvents(250);
        Set<ConflictPair> expected = naive(events);
        Set<ConflictPair> actual = optimized(events);

        // Sanidad del escenario: tiene que haber conflictos reales que comparar.
        assertThat(expected).isNotEmpty();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void mixedUniqueAndRecurring_sameConflictSetAsNaive() throws Exception {
        List<SolverEvent> events = new ArrayList<>(randomEvents(60));
        Random random = new Random(SEED + 1);
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.of(2026, 3, 2).plusDays(random.nextInt(120));
            LocalTime start = LocalTime.of(8 + random.nextInt(12), random.nextBoolean() ? 0 : 30);
            events.add(new SolverEvent("uni-" + i, 20 + random.nextInt(60),
                    start, start.plusMinutes(90), List.of(date)));
        }
        assertThat(optimized(events)).isEqualTo(naive(events));
    }

    @Test
    void emptyInput_noConflicts() throws Exception {
        assertThat(optimized(List.of())).isEmpty();
    }

    @Test
    void duplicateOccurrenceDates_doNotSelfConflictNorDuplicate() throws Exception {
        // occurrenceDates con fechas repetidas no debe generar par consigo mismo.
        LocalDate date = LocalDate.of(2026, 3, 2);
        SolverEvent a = new SolverEvent("A", 30, LocalTime.of(8, 0), LocalTime.of(9, 30),
                List.of(date, date));
        SolverEvent b = new SolverEvent("B", 30, LocalTime.of(8, 0), LocalTime.of(9, 30),
                List.of(date));
        assertThat(optimized(List.of(a, b)))
                .containsExactly(new ConflictPair("A", "B"));
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
            events.add(new SolverEvent("rec-" + i, 20 + random.nextInt(60),
                    start, start.plusMinutes(durationMinutes), weeklyDates(dow, from, to)));
        }
        return events;
    }

    private List<LocalDate> weeklyDates(DayOfWeek dow, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = from.with(TemporalAdjusters.nextOrSame(dow));
        while (!current.isAfter(to)) {
            dates.add(current);
            current = current.plusWeeks(1);
        }
        return dates;
    }
}
