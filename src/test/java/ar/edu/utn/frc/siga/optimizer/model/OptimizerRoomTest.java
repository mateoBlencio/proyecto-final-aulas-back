package ar.edu.utn.frc.siga.optimizer.model;

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
}
