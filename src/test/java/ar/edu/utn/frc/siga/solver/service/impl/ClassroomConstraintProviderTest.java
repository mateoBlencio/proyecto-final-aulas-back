package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.model.ClassAllocation;
import ar.edu.utn.frc.siga.solver.model.ScheduleSolution;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Verifica las constraints de {@link ClassroomConstraintProvider} de forma aislada
 * (cada verifyThat evalúa una sola constraint). Los ClassAllocation se arman a mano:
 * conflictingEventIds replica el formato simétrico que produce
 * {@code SolverServiceImpl.computeConflicts}.
 */
@DisplayName("ClassroomConstraintProvider")
class ClassroomConstraintProviderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

    private ConstraintVerifier<ClassroomConstraintProvider, ScheduleSolution> verifier;

    @BeforeEach
    void setUp() {
        verifier = ConstraintVerifier.build(new ClassroomConstraintProvider(), ScheduleSolution.class, ClassAllocation.class);
    }

    private static SolverEvent event(String id, String commissionKey, int enrolled, LocalTime start, LocalTime end) {
        return new SolverEvent(id, commissionKey, enrolled, start, end, Set.of(DATE));
    }

    @Nested
    @DisplayName("noOverlap (HARD)")
    class NoOverlap {

        @Test
        @DisplayName("par en conflicto en la misma aula penaliza 1 hard")
        void samRoomConflict() {
            SolverRoom room = new SolverRoom(1, 40, 100);
            SolverEvent a = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", null, 10, LocalTime.of(9, 0), LocalTime.of(11, 0));

            ClassAllocation allocA = new ClassAllocation(a, List.of(room), Set.of("b"));
            allocA.setClassroom(room);
            ClassAllocation allocB = new ClassAllocation(b, List.of(room), Set.of("a"));
            allocB.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::noOverlap)
                    .given(allocA, allocB)
                    .penalizesBy(1);
        }

        @Test
        @DisplayName("mismo horario pero aulas distintas no penaliza")
        void differentRoomsNoOverlap() {
            SolverRoom room1 = new SolverRoom(1, 40, 100);
            SolverRoom room2 = new SolverRoom(2, 40, 100);
            SolverEvent a = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", null, 10, LocalTime.of(9, 0), LocalTime.of(11, 0));

            ClassAllocation allocA = new ClassAllocation(a, List.of(room1), Set.of("b"));
            allocA.setClassroom(room1);
            ClassAllocation allocB = new ClassAllocation(b, List.of(room2), Set.of("a"));
            allocB.setClassroom(room2);

            verifier.verifyThat(ClassroomConstraintProvider::noOverlap)
                    .given(allocA, allocB)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("ambos pinned en la misma aula no penaliza (conflicto preexistente en BD, el solver no lo puede arreglar)")
        void bothPinnedNoOverlap() {
            SolverRoom room = new SolverRoom(1, 40, 100);
            SolverEvent a = event("occupied:1:2026-08-03:08:00", null, 0, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("occupied:1:2026-08-03:09:00", null, 0, LocalTime.of(9, 0), LocalTime.of(11, 0));

            ClassAllocation allocA = ClassAllocation.pinned(a, room, Set.of(b.planningId()));
            ClassAllocation allocB = ClassAllocation.pinned(b, room, Set.of(a.planningId()));

            verifier.verifyThat(ClassroomConstraintProvider::noOverlap)
                    .given(allocA, allocB)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned + nuevo en conflicto en la misma aula penaliza")
        void pinnedPlusNewConflict() {
            SolverRoom room = new SolverRoom(1, 40, 100);
            SolverEvent occupied = event("occupied:1:2026-08-03:08:00", null, 0, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent nuevo = event("nuevo", null, 10, LocalTime.of(9, 0), LocalTime.of(11, 0));

            ClassAllocation pinnedAlloc = ClassAllocation.pinned(occupied, room, Set.of(nuevo.planningId()));
            ClassAllocation newAlloc = new ClassAllocation(nuevo, List.of(room), Set.of(occupied.planningId()));
            newAlloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::noOverlap)
                    .given(pinnedAlloc, newAlloc)
                    .penalizesBy(1);
        }
    }

    @Nested
    @DisplayName("minimizeOvercrowding (SOFT)")
    class MinimizeOvercrowding {

        @Test
        @DisplayName("inscriptos por encima de la capacidad penaliza el excedente x 100.000")
        void overCapacity() {
            SolverRoom room = new SolverRoom(1, 30, 100);
            SolverEvent e = event("a", null, 35, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::minimizeOvercrowding)
                    .given(alloc)
                    .penalizesBy(5 * 100_000);
        }

        @Test
        @DisplayName("capacidad justa no penaliza")
        void exactCapacity() {
            SolverRoom room = new SolverRoom(1, 30, 100);
            SolverEvent e = event("a", null, 30, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::minimizeOvercrowding)
                    .given(alloc)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned no penaliza aunque esté sobreocupada")
        void pinnedNoImpact() {
            SolverRoom room = new SolverRoom(1, 30, 100);
            SolverEvent e = event("occupied:1:2026-08-03:08:00", null, 35, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = ClassAllocation.pinned(e, room, Set.of());

            verifier.verifyThat(ClassroomConstraintProvider::minimizeOvercrowding)
                    .given(alloc)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("sin aula asignada no penaliza sobreocupación (lo cubre assignAllPossible, no esta constraint)")
        void noRoomAssigned() {
            // Un evento sin aula (classroom null) NO penaliza sobreocupación: forEach lo excluye,
            // así que la rama `classroom == null → enrolled` de getOvercrowding() no corre durante
            // el scoring. Antes esto era "false-feasible" (un evento inubicable no dejaba rastro en
            // el score); ahora `assignAllPossible` (MEDIUM) sí lo penaliza —ver su @Nested— y con
            // allowsUnassigned el solver deja el evento sin aula → viaja en `unresolved`, no oculto.
            SolverEvent e = event("a", null, 12, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(), Set.of());
            // classroom queda null: sin aula asignada.

            verifier.verifyThat(ClassroomConstraintProvider::minimizeOvercrowding)
                    .given(alloc)
                    .hasNoImpact();
        }
    }

    @Nested
    @DisplayName("assignAllPossible (MEDIUM)")
    class AssignAllPossible {

        @Test
        @DisplayName("evento no-pinned sin aula penaliza 1 (empuja a asignar)")
        void unassignedPenalizes() {
            SolverEvent e = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(), Set.of());
            // classroom queda null.

            verifier.verifyThat(ClassroomConstraintProvider::assignAllPossible)
                    .given(alloc)
                    .penalizesBy(1);
        }

        @Test
        @DisplayName("evento con aula asignada no penaliza")
        void assignedNoImpact() {
            SolverRoom room = new SolverRoom(1, 40, 100);
            SolverEvent e = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::assignAllPossible)
                    .given(alloc)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned (siempre con aula) no penaliza")
        void pinnedNoImpact() {
            SolverRoom room = new SolverRoom(1, 40, 100);
            SolverEvent e = event("occupied:1:2026-08-03:08:00", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = ClassAllocation.pinned(e, room, Set.of());

            verifier.verifyThat(ClassroomConstraintProvider::assignAllPossible)
                    .given(alloc)
                    .hasNoImpact();
        }
    }

    @Nested
    @DisplayName("minimizeUnusedCapacity (SOFT)")
    class MinimizeUnusedCapacity {

        @Test
        @DisplayName("subocupación penaliza capacidad-inscriptos x 1")
        void underCapacity() {
            SolverRoom room = new SolverRoom(1, 40, 100);
            SolverEvent e = event("a", null, 30, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::minimizeUnusedCapacity)
                    .given(alloc)
                    .penalizesBy(10);
        }

        @Test
        @DisplayName("capacidad justa no penaliza")
        void exactCapacityNoImpact() {
            SolverRoom room = new SolverRoom(1, 40, 100);
            SolverEvent e = event("a", null, 40, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::minimizeUnusedCapacity)
                    .given(alloc)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned no penaliza aunque esté subocupada")
        void pinnedNoImpact() {
            SolverRoom room = new SolverRoom(1, 40, 100);
            SolverEvent e = event("occupied:1:2026-08-03:08:00", null, 30, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = ClassAllocation.pinned(e, room, Set.of());

            verifier.verifyThat(ClassroomConstraintProvider::minimizeUnusedCapacity)
                    .given(alloc)
                    .hasNoImpact();
        }
    }

    @Nested
    @DisplayName("preferSameRoomSameCommission (SOFT)")
    class PreferSameRoom {

        @Test
        @DisplayName("commissionKey null no penaliza")
        void nullCommissionKeyNoImpact() {
            SolverRoom room1 = new SolverRoom(1, 40, 100);
            SolverRoom room2 = new SolverRoom(2, 40, 100);
            SolverEvent a = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", null, 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

            ClassAllocation allocA = new ClassAllocation(a, List.of(room1), Set.of());
            allocA.setClassroom(room1);
            ClassAllocation allocB = new ClassAllocation(b, List.of(room2), Set.of());
            allocB.setClassroom(room2);

            verifier.verifyThat(ClassroomConstraintProvider::preferSameRoomSameCommission)
                    .given(allocA, allocB)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned no penaliza")
        void pinnedNoImpact() {
            SolverRoom room1 = new SolverRoom(1, 40, 100);
            SolverRoom room2 = new SolverRoom(2, 40, 100);
            SolverEvent a = event("occupied:1:2026-08-03:08:00", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

            ClassAllocation allocA = ClassAllocation.pinned(a, room1, Set.of());
            ClassAllocation allocB = new ClassAllocation(b, List.of(room2), Set.of());
            allocB.setClassroom(room2);

            verifier.verifyThat(ClassroomConstraintProvider::preferSameRoomSameCommission)
                    .given(allocA, allocB)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("misma comisión en aulas distintas penaliza 2.000")
        void sameCommissionDifferentRoom() {
            SolverRoom room1 = new SolverRoom(1, 40, 100);
            SolverRoom room2 = new SolverRoom(2, 40, 100);
            SolverEvent a = event("a", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

            ClassAllocation allocA = new ClassAllocation(a, List.of(room1), Set.of());
            allocA.setClassroom(room1);
            ClassAllocation allocB = new ClassAllocation(b, List.of(room2), Set.of());
            allocB.setClassroom(room2);

            verifier.verifyThat(ClassroomConstraintProvider::preferSameRoomSameCommission)
                    .given(allocA, allocB)
                    .penalizesBy(2_000);
        }

        @Test
        @DisplayName("misma comisión y misma aula no penaliza")
        void sameCommissionSameRoomNoImpact() {
            SolverRoom room = new SolverRoom(1, 40, 100);
            SolverEvent a = event("a", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

            ClassAllocation allocA = new ClassAllocation(a, List.of(room), Set.of());
            allocA.setClassroom(room);
            ClassAllocation allocB = new ClassAllocation(b, List.of(room), Set.of());
            allocB.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::preferSameRoomSameCommission)
                    .given(allocA, allocB)
                    .hasNoImpact();
        }
    }

    @Nested
    @DisplayName("preferSameBuildingSameCommission (SOFT)")
    class PreferSameBuilding {

        @Test
        @DisplayName("commissionKey null no penaliza")
        void nullCommissionKeyNoImpact() {
            SolverRoom room1 = new SolverRoom(1, 40, 100);
            SolverRoom room2 = new SolverRoom(2, 40, 200);
            SolverEvent a = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", null, 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

            ClassAllocation allocA = new ClassAllocation(a, List.of(room1), Set.of());
            allocA.setClassroom(room1);
            ClassAllocation allocB = new ClassAllocation(b, List.of(room2), Set.of());
            allocB.setClassroom(room2);

            verifier.verifyThat(ClassroomConstraintProvider::preferSameBuildingSameCommission)
                    .given(allocA, allocB)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned no penaliza")
        void pinnedNoImpact() {
            SolverRoom room1 = new SolverRoom(1, 40, 100);
            SolverRoom room2 = new SolverRoom(2, 40, 200);
            SolverEvent a = event("occupied:1:2026-08-03:08:00", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

            ClassAllocation allocA = ClassAllocation.pinned(a, room1, Set.of());
            ClassAllocation allocB = new ClassAllocation(b, List.of(room2), Set.of());
            allocB.setClassroom(room2);

            verifier.verifyThat(ClassroomConstraintProvider::preferSameBuildingSameCommission)
                    .given(allocA, allocB)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("misma comisión en edificios distintos penaliza 4.000")
        void sameCommissionDifferentBuilding() {
            SolverRoom room1 = new SolverRoom(1, 40, 100);
            SolverRoom room2 = new SolverRoom(2, 40, 200);
            SolverEvent a = event("a", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

            ClassAllocation allocA = new ClassAllocation(a, List.of(room1), Set.of());
            allocA.setClassroom(room1);
            ClassAllocation allocB = new ClassAllocation(b, List.of(room2), Set.of());
            allocB.setClassroom(room2);

            verifier.verifyThat(ClassroomConstraintProvider::preferSameBuildingSameCommission)
                    .given(allocA, allocB)
                    .penalizesBy(4_000);
        }

        @Test
        @DisplayName("misma comisión y mismo edificio no penaliza")
        void sameCommissionSameBuildingNoImpact() {
            SolverRoom room1 = new SolverRoom(1, 40, 100);
            SolverRoom room2 = new SolverRoom(2, 40, 100);
            SolverEvent a = event("a", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

            ClassAllocation allocA = new ClassAllocation(a, List.of(room1), Set.of());
            allocA.setClassroom(room1);
            ClassAllocation allocB = new ClassAllocation(b, List.of(room2), Set.of());
            allocB.setClassroom(room2);

            verifier.verifyThat(ClassroomConstraintProvider::preferSameBuildingSameCommission)
                    .given(allocA, allocB)
                    .hasNoImpact();
        }
    }
}
