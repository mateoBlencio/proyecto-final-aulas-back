package PF.classroom_allocation.optimization;

import PF.classroom_allocation.solver.model.*;
import PF.classroom_allocation.solver.optimization.ConflictPair;
import PF.classroom_allocation.solver.optimization.impl.ClassAssignment;
import PF.classroom_allocation.solver.optimization.impl.ClassroomAllocationSolverImpl;
import PF.classroom_allocation.solver.optimization.impl.ClassroomConstraintProvider;
import PF.classroom_allocation.solver.optimization.impl.ScheduleSolution;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

class ClassroomAllocationSolverTest {

    private Solver<ScheduleSolution> solver;
    private ClassroomAllocationSolverImpl solverImpl;

    @BeforeEach
    void setUp() {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(ScheduleSolution.class)
                .withEntityClasses(ClassAssignment.class)
                .withConstraintProviderClass(ClassroomConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofSeconds(5)));

        solver = SolverFactory.<ScheduleSolution>create(config).buildSolver();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RecurringEvent recurring(String id, int enrolled,
                                            DayOfWeek day,
                                            LocalTime start, int hours,
                                            LocalDate from, LocalDate to) {
        return RecurringEvent.builder()
                .id(id).enrolled(enrolled)
                .startTime(start).duration(Duration.ofHours(hours))
                .dayOfWeek(day).startDate(from).endDate(to)
                .subject(id).section("A")
                .build();
    }

    private static UniqueEvent unique(String id, int enrolled,
                                      LocalDate date,
                                      LocalTime start, int hours) {
        return UniqueEvent.builder()
                .id(id).enrolled(enrolled)
                .startTime(start).duration(Duration.ofHours(hours))
                .date(date)
                .build();
    }

    private static boolean timesOverlap(Event a, Event b) {
        return a.getStartTime().isBefore(b.endTime())
                && b.getStartTime().isBefore(a.endTime());
    }

    private static Set<ConflictPair> computeConflicts(List<Event> events) {
        Set<ConflictPair> conflicts = new HashSet<>();
        for (int i = 0; i < events.size(); i++) {
            for (int j = i + 1; j < events.size(); j++) {
                Event a = events.get(i);
                Event b = events.get(j);
                if (!timesOverlap(a, b)) continue;
                Set<LocalDate> datesA = new HashSet<>(a.occurrences());
                if (b.occurrences().stream().anyMatch(datesA::contains)) {
                    conflicts.add(new ConflictPair(a.getId(), b.getId()));
                }
            }
        }
        return conflicts;
    }

    /** Construye el problema y llama al solver de Timefold directamente */
    private ScheduleSolution solve(List<Event> events, List<Classroom> classrooms) {
        Set<ConflictPair> conflicts = computeConflicts(events);

        Map<String, List<Classroom>> candidates = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> classrooms));

        List<ClassAssignment> assignments = events.stream()
                .map(event -> {
                    Set<String> conflicting = conflicts.stream()
                            .filter(p -> p.involves(event.getId()))
                            .map(p -> p.otherEventId(event.getId()))
                            .collect(Collectors.toSet());
                    return new ClassAssignment(event, candidates.get(event.getId()), conflicting);
                })
                .toList();

        return solver.solve(new ScheduleSolution(classrooms, assignments, null));
    }

    private static void print(String title, ScheduleSolution solution,
                              List<Classroom> classrooms) {
        Map<String, Classroom> roomMap = classrooms.stream()
                .collect(Collectors.toMap(Classroom::getId, r -> r));

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  " + title);
        System.out.printf("  Score → hard: %d  soft: %d%n",
                solution.getScore().hardScore(),
                solution.getScore().softScore());
        System.out.println("-".repeat(60));
        System.out.printf("  %-10s %-18s %6s %6s %8s%n",
                "Evento", "Aula", "Cap.", "Inscr.", "Sobreoc.");
        System.out.println("-".repeat(60));

        for (ClassAssignment a : solution.getAssignments()) {
            Classroom room = a.getClassroom() != null
                    ? roomMap.get(a.getClassroom().getId()) : null;
            System.out.printf("  %-10s %-18s %6d %6d %8s%n",
                    a.getEvent().getId(),
                    room != null ? room.getName() : "—",
                    room != null ? (int) room.getSurfaceM2() : 0,
                    a.getEvent().getEnrolled(),
                    a.getOvercrowding() > 0 ? "+" + a.getOvercrowding() : "OK");
        }
        System.out.println("=".repeat(60));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void noConflicts_optimalDistribution() {
        List<Classroom> rooms = List.of(
                new Classroom("R1", "Aula 01",  30),
                new Classroom("R2", "Aula 02",  60),
                new Classroom("R3", "Aula 03", 100)
        );
        List<Event> events = List.of(
                recurring("E-PRG", 25, DayOfWeek.MONDAY,
                        LocalTime.of(8,  0), 2,
                        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 7, 31)),
                recurring("E-ALG", 55, DayOfWeek.MONDAY,
                        LocalTime.of(10, 0), 2,
                        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 7, 31)),
                recurring("E-FIS", 90, DayOfWeek.MONDAY,
                        LocalTime.of(12, 0), 2,
                        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 7, 31))
        );

        print("Sin conflictos — distribución óptima", solve(events, rooms), rooms);
    }

    @Test
    void twoConflictingEvents_twoRooms_feasible() {
        List<Classroom> rooms = List.of(
                new Classroom("R1", "Aula 01", 60),
                new Classroom("R2", "Aula 02", 80)
        );
        List<Event> events = List.of(
                recurring("E-MAT", 55, DayOfWeek.TUESDAY,
                        LocalTime.of(9, 0), 2,
                        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 7, 31)),
                recurring("E-QUI", 45, DayOfWeek.TUESDAY,
                        LocalTime.of(10, 0), 2,     // solapan 10:00–11:00
                        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 7, 31))
        );

        print("Dos eventos en conflicto — factible", solve(events, rooms), rooms);
    }

    @Test
    void twoConflictingEvents_oneRoom_infeasible() {
        List<Classroom> rooms = List.of(
                new Classroom("R1", "Aula 01", 60)
        );
        List<Event> events = List.of(
                recurring("E-MAT", 40, DayOfWeek.WEDNESDAY,
                        LocalTime.of(8, 0), 3,
                        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 7, 31)),
                unique("E-PARC", 40,
                        LocalDate.of(2025, 4, 16),   // miércoles
                        LocalTime.of(9, 0), 2)
        );

        print("Conflicto sin salida — hard violado", solve(events, rooms), rooms);
    }

    @Test
    void overcrowding_assignsLargestRoom() {
        List<Classroom> rooms = List.of(
                new Classroom("R1", "Aula 01",  30),
                new Classroom("R2", "Aula 02",  60),
                new Classroom("R3", "Aula 03", 100)
        );
        List<Event> events = List.of(
                recurring("E-CAL", 140, DayOfWeek.FRIDAY,
                        LocalTime.of(9, 0), 2,
                        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 7, 31))
        );

        print("Sobreocupación inevitable — asigna mayor aula", solve(events, rooms), rooms);
    }

    @Test
    void recurringVsUnique_sameDayAndTime() {
        List<Classroom> rooms = List.of(
                new Classroom("R1", "Aula 01", 100),
                new Classroom("R2", "Aula 02", 150)
        );
        List<Event> events = List.of(
                recurring("E-ORG", 70, DayOfWeek.THURSDAY,
                        LocalTime.of(14, 0), 2,
                        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 7, 31)),
                unique("E-FIN", 65,
                        LocalDate.of(2025, 5, 8),    // jueves
                        LocalTime.of(14, 0), 2)
        );

        print("Franja regular vs final — mismo día y hora", solve(events, rooms), rooms);
    }
}