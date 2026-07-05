package ar.edu.utn.frc.siga.solver.model;

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
    void upCp005_constructor_normalizesOrder() {
        ConflictPair pair = new ConflictPair("B", "A");
        assertThat(pair.eventIdA()).isEqualTo("A");
        assertThat(pair.eventIdB()).isEqualTo("B");
    }

    @Test
    void upCp006_constructor_preservesOrderWhenCanonical() {
        ConflictPair pair = new ConflictPair("A", "B");
        assertThat(pair.eventIdA()).isEqualTo("A");
        assertThat(pair.eventIdB()).isEqualTo("B");
    }

    @Test
    void upCp007_constructor_sameEvent_throws() {
        assertThatThrownBy(() -> new ConflictPair("A", "A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upCp008_constructor_nullEvent_throws() {
        assertThatThrownBy(() -> new ConflictPair("A", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConflictPair(null, "B"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void upCp010_setDedup_symmetric() {
        Set<ConflictPair> set = new HashSet<>(Set.of(new ConflictPair("A", "B")));
        set.add(new ConflictPair("B", "A"));
        assertThat(set).hasSize(1);
    }
}
