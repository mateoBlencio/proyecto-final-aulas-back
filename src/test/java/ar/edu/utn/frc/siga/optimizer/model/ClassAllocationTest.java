package ar.edu.utn.frc.siga.optimizer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassAllocation")
class ClassAllocationTest {

    private static OptimizerEvent event(String commissionKey, int enrolled) {
        return new OptimizerEvent("a", commissionKey, enrolled,
                LocalTime.of(8, 0), LocalTime.of(10, 0), Set.of(LocalDate.of(2026, 8, 3)));
    }

    @Test
    @DisplayName("getOvercrowding sin aula asignada devuelve los inscriptos totales")
    void overcrowdingWithoutClassroom() {
        ClassAllocation allocation = new ClassAllocation(event(null, 12), List.of(), Set.of());

        assertThat(allocation.getOvercrowding()).isEqualTo(12);
    }

    @Test
    @DisplayName("getOvercrowding con aula asignada delega en OptimizerRoom.overcrowding")
    void overcrowdingWithClassroom() {
        OptimizerRoom room = new OptimizerRoom(1L, 10, 100L);
        ClassAllocation allocation = new ClassAllocation(event(null, 15), List.of(room), Set.of());
        allocation.setClassroom(room);

        assertThat(allocation.getOvercrowding()).isEqualTo(5);
    }

    @Test
    @DisplayName("getUnusedCapacity sin aula asignada devuelve 0")
    void unusedCapacityWithoutClassroom() {
        ClassAllocation allocation = new ClassAllocation(event(null, 12), List.of(), Set.of());

        assertThat(allocation.getUnusedCapacity()).isZero();
    }

    @Test
    @DisplayName("getUnusedCapacity con aula asignada delega en OptimizerRoom.undercrowding")
    void unusedCapacityWithClassroom() {
        OptimizerRoom room = new OptimizerRoom(1L, 30, 100L);
        ClassAllocation allocation = new ClassAllocation(event(null, 20), List.of(room), Set.of());
        allocation.setClassroom(room);

        assertThat(allocation.getUnusedCapacity()).isEqualTo(10);
    }

    @Test
    @DisplayName("conflictsWith consulta el set de conflictingEventIds precalculado")
    void conflictsWith() {
        ClassAllocation allocation = new ClassAllocation(event(null, 10), List.of(), Set.of("b"));

        assertThat(allocation.conflictsWith("b")).isTrue();
        assertThat(allocation.conflictsWith("c")).isFalse();
    }

    @Test
    @DisplayName("getCommissionKey delega en el evento")
    void commissionKey() {
        ClassAllocation allocation = new ClassAllocation(event("1K1", 10), List.of(), Set.of());

        assertThat(allocation.getCommissionKey()).isEqualTo("1K1");
    }

    @Test
    @DisplayName("getBuildingId sin aula asignada devuelve null")
    void buildingIdWithoutClassroom() {
        ClassAllocation allocation = new ClassAllocation(event(null, 10), List.of(), Set.of());

        assertThat(allocation.getBuildingId()).isNull();
    }

    @Test
    @DisplayName("getBuildingId con aula asignada delega en el aula")
    void buildingIdWithClassroom() {
        OptimizerRoom room = new OptimizerRoom(1L, 30, 100L);
        ClassAllocation allocation = new ClassAllocation(event(null, 10), List.of(room), Set.of());
        allocation.setClassroom(room);

        assertThat(allocation.getBuildingId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("pinned() fija el aula como única candidata y marca pinned=true")
    void pinnedFactory() {
        OptimizerRoom room = new OptimizerRoom(1L, 30, 100L);
        OptimizerEvent occupiedEvent = event(null, 0);

        ClassAllocation allocation = ClassAllocation.pinned(occupiedEvent, room, Set.of("x"));

        assertThat(allocation.isPinned()).isTrue();
        assertThat(allocation.getClassroom()).isEqualTo(room);
        assertThat(allocation.getCandidates()).containsExactly(room);
        assertThat(allocation.conflictsWith("x")).isTrue();
    }
}
