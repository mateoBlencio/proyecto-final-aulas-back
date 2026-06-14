package PF.classroom_allocation.solver.optimization;

import PF.classroom_allocation.solver.model.Classroom;
import PF.classroom_allocation.solver.model.UniqueEvent;
import PF.classroom_allocation.solver.optimization.impl.ClassAssignment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClassroomConstraintProviderTest {

    private static final int OVERCROWDING_WEIGHT = 100_000;

    private Classroom room(String id, float cap) {
        return new Classroom(id, "Room " + id, cap);
    }

    private ClassAssignment assigned(String eventId, int enrolled, Classroom classroom,
                                     Set<String> conflictingIds) {
        UniqueEvent event = UniqueEvent.builder()
                .id(eventId).enrolled(enrolled)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .date(LocalDate.of(2024, 1, 1))
                .build();
        ClassAssignment a = new ClassAssignment(event, List.of(classroom), conflictingIds);
        a.setClassroom(classroom);
        return a;
    }

    private ClassAssignment unassigned(String eventId, int enrolled, Classroom candidate) {
        UniqueEvent event = UniqueEvent.builder()
                .id(eventId).enrolled(enrolled)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .date(LocalDate.of(2024, 1, 1))
                .build();
        return new ClassAssignment(event, List.of(candidate), Set.of());
    }

    // ─── noOverlap ───────────────────────────────────────────────────────────

    @Test
    void upCn001_sameClassroom_conflictPair_penalizesOne() {
        Classroom room = room("r1", 100);
        ClassAssignment a1 = assigned("e1", 50, room, Set.of("e2"));
        ClassAssignment a2 = assigned("e2", 50, room, Set.of("e1"));

        assertThat(a1.getClassroom()).isEqualTo(a2.getClassroom());
        assertThat(a1.conflictsWith(a2.getEvent().getId())).isTrue();
    }

    @Test
    void upCn002_sameClassroom_noConflictPair_noImpact() {
        Classroom room = room("r1", 100);
        ClassAssignment a1 = assigned("e1", 50, room, Set.of());
        ClassAssignment a2 = assigned("e2", 50, room, Set.of());

        assertThat(a1.conflictsWith(a2.getEvent().getId())).isFalse();
        assertThat(a2.conflictsWith(a1.getEvent().getId())).isFalse();
    }

    @Test
    void upCn003_differentClassrooms_conflictPair_noImpact() {
        Classroom room1 = room("r1", 100);
        Classroom room2 = room("r2", 100);
        ClassAssignment a1 = assigned("e1", 50, room1, Set.of("e2"));
        ClassAssignment a2 = assigned("e2", 50, room2, Set.of("e1"));

        assertThat(a1.getClassroom()).isNotEqualTo(a2.getClassroom());
    }

    @Test
    void upCn004_sameClassroom_sameTime_notInConflictIds_noImpact() {
        Classroom room = room("r1", 100);
        ClassAssignment a1 = assigned("e1", 50, room, Set.of());
        ClassAssignment a2 = assigned("e2", 50, room, Set.of());

        assertThat(a1.conflictsWith(a2.getEvent().getId())).isFalse();
    }

    @Test
    void upCn005_singleAssignment_noImpact() {
        Classroom room = room("r1", 100);
        ClassAssignment a1 = assigned("e1", 50, room, Set.of());

        assertThat(a1.conflictsWith("e1")).isFalse();
    }

    // ─── minimizeOvercrowding ────────────────────────────────────────────────

    @Test
    void upCn006_noOvercrowding_exact_noImpact() {
        Classroom room = room("r1", 80);
        ClassAssignment a = assigned("e1", 80, room, Set.of());

        assertThat(a.getOvercrowding()).isEqualTo(0);
    }

    @Test
    void upCn007_overcrowding10_penalty1M() {
        Classroom room = room("r1", 80);
        ClassAssignment a = assigned("e1", 90, room, Set.of());

        assertThat((long) a.getOvercrowding() * OVERCROWDING_WEIGHT).isEqualTo(10L * 100_000L);
    }

    @Test
    void upCn008_enrolled50_cap80_noOvercrowding() {
        Classroom room = room("r1", 80);
        ClassAssignment a = assigned("e1", 50, room, Set.of());

        assertThat(a.getOvercrowding()).isEqualTo(0);
    }

    @Test
    void upCn009_overcrowding40_penalty4M() {
        Classroom room = room("r1", 125);
        ClassAssignment a = assigned("e1", 165, room, Set.of());

        assertThat((long) a.getOvercrowding() * OVERCROWDING_WEIGHT).isEqualTo(40L * 100_000L);
    }

    @Test
    void upCn010_nullClassroom_noImpact() {
        Classroom room = room("r1", 80);
        ClassAssignment a = unassigned("e1", 50, room);

        assertThat(a.getClassroom()).isNull();
    }

    // ─── minimizeUnusedCapacity ──────────────────────────────────────────────

    @Test
    void upCn011_fullCapacity_noImpact() {
        Classroom room = room("r1", 80);
        ClassAssignment a = assigned("e1", 80, room, Set.of());

        assertThat(a.getUnusedCapacity()).isEqualTo(0);
    }

    @Test
    void upCn012_unused30_penalty30() {
        Classroom room = room("r1", 80);
        ClassAssignment a = assigned("e1", 50, room, Set.of());

        assertThat(a.getUnusedCapacity()).isEqualTo(30);
    }

    @Test
    void upCn013_overcrowded_unused0_noImpact() {
        Classroom room = room("r1", 80);
        ClassAssignment a = assigned("e1", 90, room, Set.of());

        assertThat(a.getUnusedCapacity()).isEqualTo(0);
    }

    @Test
    void upCn014_enrolled1_cap160_penalty159() {
        Classroom room = room("r1", 160);
        ClassAssignment a = assigned("e1", 1, room, Set.of());

        assertThat(a.getUnusedCapacity()).isEqualTo(159);
    }

    @Test
    void upCn015_nullClassroom_noImpact() {
        Classroom room = room("r1", 80);
        ClassAssignment a = unassigned("e1", 50, room);

        assertThat(a.getUnusedCapacity()).isEqualTo(0);
    }
}
