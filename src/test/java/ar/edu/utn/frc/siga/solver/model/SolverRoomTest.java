package ar.edu.utn.frc.siga.solver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SolverRoom")
class SolverRoomTest {

    @Test
    @DisplayName("overcrowding: inscriptos por encima de la capacidad devuelve el excedente")
    void overcrowdingWhenOverCapacity() {
        SolverRoom room = new SolverRoom(1, 30, 100);

        assertThat(room.overcrowding(35)).isEqualTo(5);
    }

    @Test
    @DisplayName("overcrowding: inscriptos por debajo o igual a la capacidad se clampea a 0")
    void overcrowdingClampsToZero() {
        SolverRoom room = new SolverRoom(1, 30, 100);

        assertThat(room.overcrowding(30)).isZero();
        assertThat(room.overcrowding(20)).isZero();
    }

    @Test
    @DisplayName("undercrowding: inscriptos por debajo de la capacidad devuelve los asientos libres")
    void undercrowdingWhenUnderCapacity() {
        SolverRoom room = new SolverRoom(1, 30, 100);

        assertThat(room.undercrowding(20)).isEqualTo(10);
    }

    @Test
    @DisplayName("undercrowding: inscriptos por encima o igual a la capacidad se clampea a 0")
    void undercrowdingClampsToZero() {
        SolverRoom room = new SolverRoom(1, 30, 100);

        assertThat(room.undercrowding(30)).isZero();
        assertThat(room.undercrowding(35)).isZero();
    }
}
