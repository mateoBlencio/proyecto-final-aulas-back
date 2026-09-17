package ar.edu.utn.frc.siga.optimizer.constraint;

import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PermissionNotSatisfied.violates")
class PermissionNotSatisfiedTest {

    private static final OptimizerRoom OPEN_TO_ALL =
            new OptimizerRoom(1L, 40, 100L, true, Set.of());
    private static final OptimizerRoom SUBSET_10 =
            new OptimizerRoom(2L, 40, 100L, false, Set.of(10L));
    private static final OptimizerRoom NONE =
            new OptimizerRoom(3L, 40, 100L, false, Set.of());

    private static OptimizerEvent eventForSubject(Long subjectId) {
        Set<Long> subjectIds = subjectId == null ? Set.of() : Set.of(subjectId);
        return new OptimizerEvent("ev-1", "K1001", 30,
                LocalTime.of(8, 0), LocalTime.of(10, 0), Set.of(), subjectIds);
    }

    private static ClassAllocation assigned(OptimizerEvent event, OptimizerRoom room) {
        ClassAllocation allocation = new ClassAllocation(event, List.of(room), Set.of());
        allocation.setClassroom(room);
        return allocation;
    }

    @Test
    @DisplayName("aula abierta a todas: no viola")
    void openToAll() {
        assertThat(PermissionNotSatisfied.violates(assigned(eventForSubject(10L), OPEN_TO_ALL))).isFalse();
    }

    @Test
    @DisplayName("aula SUBSET que incluye la materia: no viola")
    void subsetMatch() {
        assertThat(PermissionNotSatisfied.violates(assigned(eventForSubject(10L), SUBSET_10))).isFalse();
    }

    @Test
    @DisplayName("aula SUBSET que no incluye la materia: viola")
    void subsetMiss() {
        assertThat(PermissionNotSatisfied.violates(assigned(eventForSubject(99L), SUBSET_10))).isTrue();
    }

    @Test
    @DisplayName("aula NONE: viola")
    void none() {
        assertThat(PermissionNotSatisfied.violates(assigned(eventForSubject(10L), NONE))).isTrue();
    }

    @Test
    @DisplayName("evento sin materia en aula SUBSET: viola (permits(Set.of()) es false salvo openToAll)")
    void eventWithoutSubject() {
        assertThat(PermissionNotSatisfied.violates(assigned(eventForSubject(null), SUBSET_10))).isTrue();
    }

    @Test
    @DisplayName("evento sin materia en aula abierta a todas: no viola")
    void eventWithoutSubjectOpenRoom() {
        assertThat(PermissionNotSatisfied.violates(assigned(eventForSubject(null), OPEN_TO_ALL))).isFalse();
    }

    @Test
    @DisplayName("asignación fijada en aula no permitida: no viola (el solver no la puede corregir)")
    void pinnedIsExcluded() {
        ClassAllocation pinned = ClassAllocation.pinned(eventForSubject(99L), SUBSET_10, Set.of());
        assertThat(PermissionNotSatisfied.violates(pinned)).isFalse();
    }

    @Test
    @DisplayName("asignación sin aula: no viola")
    void unassigned() {
        ClassAllocation unassigned = new ClassAllocation(eventForSubject(99L), List.of(SUBSET_10), Set.of());
        assertThat(PermissionNotSatisfied.violates(unassigned)).isFalse();
    }
}
