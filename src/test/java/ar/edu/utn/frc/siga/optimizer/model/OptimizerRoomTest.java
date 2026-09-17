package ar.edu.utn.frc.siga.optimizer.model;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OptimizerRoom")
class OptimizerRoomTest {

    @Test
    @DisplayName("overcrowding: inscriptos por encima de la capacidad devuelve el excedente")
    void overcrowdingWhenOverCapacity() {
        OptimizerRoom room = new OptimizerRoom(1L, 30, 100L);

        assertThat(room.overcrowding(35)).isEqualTo(5);
    }

    @Test
    @DisplayName("overcrowding: inscriptos por debajo o igual a la capacidad se clampea a 0")
    void overcrowdingClampsToZero() {
        OptimizerRoom room = new OptimizerRoom(1L, 30, 100L);

        assertThat(room.overcrowding(30)).isZero();
        assertThat(room.overcrowding(20)).isZero();
    }

    @Test
    @DisplayName("undercrowding: inscriptos por debajo de la capacidad devuelve los asientos libres")
    void undercrowdingWhenUnderCapacity() {
        OptimizerRoom room = new OptimizerRoom(1L, 30, 100L);

        assertThat(room.undercrowding(20)).isEqualTo(10);
    }

    @Test
    @DisplayName("undercrowding: inscriptos por encima o igual a la capacidad se clampea a 0")
    void undercrowdingClampsToZero() {
        OptimizerRoom room = new OptimizerRoom(1L, 30, 100L);

        assertThat(room.undercrowding(30)).isZero();
        assertThat(room.undercrowding(35)).isZero();
    }

    @Test
    @DisplayName("permits: el constructor de 3 args deja el aula abierta a toda materia")
    void permitsOpenByDefault() {
        OptimizerRoom room = new OptimizerRoom(1L, 30, 100L);

        assertThat(room.permits(Set.of(7L))).isTrue();
        assertThat(room.openToAll()).isTrue();
    }

    @Test
    @DisplayName("permits: SUBSET habilita solo si comparte al menos una materia; NONE nunca")
    void permitsSubsetAndNone() {
        OptimizerRoom subset = new OptimizerRoom(1L, 30, 100L, false, Set.of(7L, 8L));
        assertThat(subset.permits(Set.of(8L, 9L))).isTrue();
        assertThat(subset.permits(Set.of(9L))).isFalse();
        assertThat(subset.permits(Set.of())).isFalse();

        OptimizerRoom none = new OptimizerRoom(1L, 30, 100L, false, Set.of());
        assertThat(none.permits(Set.of(7L))).isFalse();
    }
}
