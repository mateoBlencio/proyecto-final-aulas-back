package ar.edu.utn.frc.siga.optimizer.service.impl;

import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ar.edu.utn.frc.siga.optimizer.model.ScheduleSolution;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@DisplayName("ClassroomConstraintProvider")
class ClassroomConstraintProviderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

    private ConstraintVerifier<ClassroomConstraintProvider, ScheduleSolution> verifier;

    @BeforeEach
    void setUp() {
        verifier = ConstraintVerifier.build(new ClassroomConstraintProvider(), ScheduleSolution.class, ClassAllocation.class);
    }

    private static OptimizerEvent event(String id, String commissionKey, int enrolled, LocalTime start, LocalTime end) {
        return new OptimizerEvent(id, commissionKey, enrolled, start, end, Set.of(DATE));
    }

    @Nested
    @DisplayName("noOverlap (HARD)")
    class NoOverlap {

        @Test
        @DisplayName("par en conflicto en la misma aula penaliza 1 hard")
        void samRoomConflict() {
            OptimizerRoom room = new OptimizerRoom(1, 40, 100);
            OptimizerEvent a = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", null, 10, LocalTime.of(9, 0), LocalTime.of(11, 0));

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
            OptimizerRoom room1 = new OptimizerRoom(1, 40, 100);
            OptimizerRoom room2 = new OptimizerRoom(2, 40, 100);
            OptimizerEvent a = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", null, 10, LocalTime.of(9, 0), LocalTime.of(11, 0));

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
            OptimizerRoom room = new OptimizerRoom(1, 40, 100);
            OptimizerEvent a = event("occupied:1:2026-08-03:08:00", null, 0, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("occupied:1:2026-08-03:09:00", null, 0, LocalTime.of(9, 0), LocalTime.of(11, 0));

            ClassAllocation allocA = ClassAllocation.pinned(a, room, Set.of(b.planningId()));
            ClassAllocation allocB = ClassAllocation.pinned(b, room, Set.of(a.planningId()));

            verifier.verifyThat(ClassroomConstraintProvider::noOverlap)
                    .given(allocA, allocB)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned + nuevo en conflicto en la misma aula penaliza")
        void pinnedPlusNewConflict() {
            OptimizerRoom room = new OptimizerRoom(1, 40, 100);
            OptimizerEvent occupied = event("occupied:1:2026-08-03:08:00", null, 0, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent nuevo = event("nuevo", null, 10, LocalTime.of(9, 0), LocalTime.of(11, 0));

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
            OptimizerRoom room = new OptimizerRoom(1, 30, 100);
            OptimizerEvent e = event("a", null, 35, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::minimizeOvercrowding)
                    .given(alloc)
                    .penalizesBy(5 * 100_000);
        }

        @Test
        @DisplayName("capacidad justa no penaliza")
        void exactCapacity() {
            OptimizerRoom room = new OptimizerRoom(1, 30, 100);
            OptimizerEvent e = event("a", null, 30, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::minimizeOvercrowding)
                    .given(alloc)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned no penaliza aunque esté sobreocupada")
        void pinnedNoImpact() {
            OptimizerRoom room = new OptimizerRoom(1, 30, 100);
            OptimizerEvent e = event("occupied:1:2026-08-03:08:00", null, 35, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = ClassAllocation.pinned(e, room, Set.of());

            verifier.verifyThat(ClassroomConstraintProvider::minimizeOvercrowding)
                    .given(alloc)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("sin aula asignada no penaliza sobreocupación (lo cubre allocateAllPossible, no esta constraint)")
        void noRoomAllocated() {
            OptimizerEvent e = event("a", null, 12, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(), Set.of());

            verifier.verifyThat(ClassroomConstraintProvider::minimizeOvercrowding)
                    .given(alloc)
                    .hasNoImpact();
        }
    }

    @Nested
    @DisplayName("allocateAllPossible (MEDIUM)")
    class AllocateAllPossible {

        @Test
        @DisplayName("evento no-pinned sin aula penaliza 1 (empuja a asignar)")
        void unallocatedPenalizes() {
            OptimizerEvent e = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(), Set.of());

            verifier.verifyThat(ClassroomConstraintProvider::allocateAllPossible)
                    .given(alloc)
                    .penalizesBy(1);
        }

        @Test
        @DisplayName("evento con aula asignada no penaliza")
        void allocatedNoImpact() {
            OptimizerRoom room = new OptimizerRoom(1, 40, 100);
            OptimizerEvent e = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::allocateAllPossible)
                    .given(alloc)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned (siempre con aula) no penaliza")
        void pinnedNoImpact() {
            OptimizerRoom room = new OptimizerRoom(1, 40, 100);
            OptimizerEvent e = event("occupied:1:2026-08-03:08:00", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = ClassAllocation.pinned(e, room, Set.of());

            verifier.verifyThat(ClassroomConstraintProvider::allocateAllPossible)
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
            OptimizerRoom room = new OptimizerRoom(1, 40, 100);
            OptimizerEvent e = event("a", null, 30, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::minimizeUnusedCapacity)
                    .given(alloc)
                    .penalizesBy(10);
        }

        @Test
        @DisplayName("capacidad justa no penaliza")
        void exactCapacityNoImpact() {
            OptimizerRoom room = new OptimizerRoom(1, 40, 100);
            OptimizerEvent e = event("a", null, 40, LocalTime.of(8, 0), LocalTime.of(10, 0));
            ClassAllocation alloc = new ClassAllocation(e, List.of(room), Set.of());
            alloc.setClassroom(room);

            verifier.verifyThat(ClassroomConstraintProvider::minimizeUnusedCapacity)
                    .given(alloc)
                    .hasNoImpact();
        }

        @Test
        @DisplayName("pinned no penaliza aunque esté subocupada")
        void pinnedNoImpact() {
            OptimizerRoom room = new OptimizerRoom(1, 40, 100);
            OptimizerEvent e = event("occupied:1:2026-08-03:08:00", null, 30, LocalTime.of(8, 0), LocalTime.of(10, 0));
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
            OptimizerRoom room1 = new OptimizerRoom(1, 40, 100);
            OptimizerRoom room2 = new OptimizerRoom(2, 40, 100);
            OptimizerEvent a = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", null, 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

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
            OptimizerRoom room1 = new OptimizerRoom(1, 40, 100);
            OptimizerRoom room2 = new OptimizerRoom(2, 40, 100);
            OptimizerEvent a = event("occupied:1:2026-08-03:08:00", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

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
            OptimizerRoom room1 = new OptimizerRoom(1, 40, 100);
            OptimizerRoom room2 = new OptimizerRoom(2, 40, 100);
            OptimizerEvent a = event("a", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

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
            OptimizerRoom room = new OptimizerRoom(1, 40, 100);
            OptimizerEvent a = event("a", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

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
            OptimizerRoom room1 = new OptimizerRoom(1, 40, 100);
            OptimizerRoom room2 = new OptimizerRoom(2, 40, 200);
            OptimizerEvent a = event("a", null, 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", null, 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

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
            OptimizerRoom room1 = new OptimizerRoom(1, 40, 100);
            OptimizerRoom room2 = new OptimizerRoom(2, 40, 200);
            OptimizerEvent a = event("occupied:1:2026-08-03:08:00", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

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
            OptimizerRoom room1 = new OptimizerRoom(1, 40, 100);
            OptimizerRoom room2 = new OptimizerRoom(2, 40, 200);
            OptimizerEvent a = event("a", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

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
            OptimizerRoom room1 = new OptimizerRoom(1, 40, 100);
            OptimizerRoom room2 = new OptimizerRoom(2, 40, 100);
            OptimizerEvent a = event("a", "1K1", 10, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerEvent b = event("b", "1K1", 10, LocalTime.of(11, 0), LocalTime.of(12, 0));

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
