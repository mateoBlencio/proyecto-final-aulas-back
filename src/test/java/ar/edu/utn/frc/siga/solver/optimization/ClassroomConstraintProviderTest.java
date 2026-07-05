package ar.edu.utn.frc.siga.solver.optimization;

import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClassroomConstraintProviderTest {

    private static final int OVERCROWDING_WEIGHT = 100_000;

    private SolverRoom room(Integer id, int cap) {
        return new SolverRoom(id, cap);
    }

    private SolverEvent event(String eventId, int enrolled) {
        return new SolverEvent(eventId, enrolled, LocalTime.of(8, 0), LocalTime.of(9, 30),
                Set.of(LocalDate.of(2024, 1, 1)));
    }

    private ClassAssignment assigned(String eventId, int enrolled, SolverRoom classroom,
                                     Set<String> conflictingIds) {
        ClassAssignment a = new ClassAssignment(event(eventId, enrolled), List.of(classroom), conflictingIds);
        a.setClassroom(classroom);
        return a;
    }

    private ClassAssignment unassigned(String eventId, int enrolled, SolverRoom candidate) {
        return new ClassAssignment(event(eventId, enrolled), List.of(candidate), Set.of());
    }

    // ─── noOverlap ───────────────────────────────────────────────────────────

    @Test
    void upCn001_sameClassroom_conflictPair_penalizesOne() {
        SolverRoom room = room(1, 100);
        ClassAssignment a1 = assigned("e1", 50, room, Set.of("e2"));
        ClassAssignment a2 = assigned("e2", 50, room, Set.of("e1"));

        assertThat(a1.getClassroom()).isEqualTo(a2.getClassroom());
        assertThat(a1.conflictsWith(a2.getEvent().planningId())).isTrue();
    }

    @Test
    void upCn002_sameClassroom_noConflictPair_noImpact() {
        SolverRoom room = room(1, 100);
        ClassAssignment a1 = assigned("e1", 50, room, Set.of());
        ClassAssignment a2 = assigned("e2", 50, room, Set.of());

        assertThat(a1.conflictsWith(a2.getEvent().planningId())).isFalse();
        assertThat(a2.conflictsWith(a1.getEvent().planningId())).isFalse();
    }

    @Test
    void upCn003_differentClassrooms_conflictPair_noImpact() {
        SolverRoom room1 = room(1, 100);
        SolverRoom room2 = room(2, 100);
        ClassAssignment a1 = assigned("e1", 50, room1, Set.of("e2"));
        ClassAssignment a2 = assigned("e2", 50, room2, Set.of("e1"));

        assertThat(a1.getClassroom()).isNotEqualTo(a2.getClassroom());
    }

    @Test
    void upCn004_sameClassroom_sameTime_notInConflictIds_noImpact() {
        SolverRoom room = room(1, 100);
        ClassAssignment a1 = assigned("e1", 50, room, Set.of());
        ClassAssignment a2 = assigned("e2", 50, room, Set.of());

        assertThat(a1.conflictsWith(a2.getEvent().planningId())).isFalse();
    }

    @Test
    void upCn005_singleAssignment_noImpact() {
        SolverRoom room = room(1, 100);
        ClassAssignment a1 = assigned("e1", 50, room, Set.of());

        assertThat(a1.conflictsWith("e1")).isFalse();
    }

    // ─── minimizeOvercrowding ────────────────────────────────────────────────

    @Test
    void upCn006_noOvercrowding_exact_noImpact() {
        SolverRoom room = room(1, 80);
        ClassAssignment a = assigned("e1", 80, room, Set.of());

        assertThat(a.getOvercrowding()).isEqualTo(0);
    }

    @Test
    void upCn007_overcrowding10_penalty1M() {
        SolverRoom room = room(1, 80);
        ClassAssignment a = assigned("e1", 90, room, Set.of());

        assertThat((long) a.getOvercrowding() * OVERCROWDING_WEIGHT).isEqualTo(10L * 100_000L);
    }

    @Test
    void upCn008_enrolled50_cap80_noOvercrowding() {
        SolverRoom room = room(1, 80);
        ClassAssignment a = assigned("e1", 50, room, Set.of());

        assertThat(a.getOvercrowding()).isEqualTo(0);
    }

    @Test
    void upCn009_overcrowding40_penalty4M() {
        SolverRoom room = room(1, 125);
        ClassAssignment a = assigned("e1", 165, room, Set.of());

        assertThat((long) a.getOvercrowding() * OVERCROWDING_WEIGHT).isEqualTo(40L * 100_000L);
    }

    @Test
    void upCn010_nullClassroom_noImpact() {
        SolverRoom room = room(1, 80);
        ClassAssignment a = unassigned("e1", 50, room);

        assertThat(a.getClassroom()).isNull();
    }

    // ─── minimizeUnusedCapacity ──────────────────────────────────────────────

    @Test
    void upCn011_fullCapacity_noImpact() {
        SolverRoom room = room(1, 80);
        ClassAssignment a = assigned("e1", 80, room, Set.of());

        assertThat(a.getUnusedCapacity()).isEqualTo(0);
    }

    @Test
    void upCn012_unused30_penalty30() {
        SolverRoom room = room(1, 80);
        ClassAssignment a = assigned("e1", 50, room, Set.of());

        assertThat(a.getUnusedCapacity()).isEqualTo(30);
    }

    @Test
    void upCn013_overcrowded_unused0_noImpact() {
        SolverRoom room = room(1, 80);
        ClassAssignment a = assigned("e1", 90, room, Set.of());

        assertThat(a.getUnusedCapacity()).isEqualTo(0);
    }

    @Test
    void upCn014_enrolled1_cap160_penalty159() {
        SolverRoom room = room(1, 160);
        ClassAssignment a = assigned("e1", 1, room, Set.of());

        assertThat(a.getUnusedCapacity()).isEqualTo(159);
    }

    @Test
    void upCn015_nullClassroom_noImpact() {
        SolverRoom room = room(1, 80);
        ClassAssignment a = unassigned("e1", 50, room);

        assertThat(a.getUnusedCapacity()).isEqualTo(0);
    }
}
