package PF.classroom_allocation.solver.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConflictPairTest {

    @Test
    void upCp001_equals_samePair() {
        assertThat(new ConflictPair("A", "B")).isEqualTo(new ConflictPair("A", "B"));
    }

    @Test
    void upCp002_equals_symmetric() {
        assertThat(new ConflictPair("A", "B")).isEqualTo(new ConflictPair("B", "A"));
    }

    @Test
    void upCp003_equals_different() {
        assertThat(new ConflictPair("A", "B")).isNotEqualTo(new ConflictPair("A", "C"));
    }

    @Test
    void upCp004_hashCode_symmetric() {
        assertThat(new ConflictPair("A", "B").hashCode())
                .isEqualTo(new ConflictPair("B", "A").hashCode());
    }

    @Test
    void upCp005_involves_matchA() {
        assertThat(new ConflictPair("A", "B").involves("A")).isTrue();
    }

    @Test
    void upCp006_involves_matchB() {
        assertThat(new ConflictPair("A", "B").involves("B")).isTrue();
    }

    @Test
    void upCp007_involves_noMatch() {
        assertThat(new ConflictPair("A", "B").involves("C")).isFalse();
    }

    @Test
    void upCp008_otherEventId_fromA() {
        assertThat(new ConflictPair("A", "B").otherEventId("A")).isEqualTo("B");
    }

    @Test
    void upCp009_otherEventId_fromB() {
        assertThat(new ConflictPair("A", "B").otherEventId("B")).isEqualTo("A");
    }

    @Test
    void upCp010_setDedup_symmetric() {
        Set<ConflictPair> set = new HashSet<>(Set.of(new ConflictPair("A", "B")));
        set.add(new ConflictPair("B", "A"));
        assertThat(set).hasSize(1);
    }

    @Test
    void otherEventId_unknownEvent_throws() {
        assertThatThrownBy(() -> new ConflictPair("A", "B").otherEventId("C"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
