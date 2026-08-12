package ar.edu.utn.frc.siga.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Overcrowding")
class OvercrowdingTest {

    @Test
    @DisplayName("by: inscriptos por encima de la capacidad devuelve el excedente")
    void porEncimaDevuelveExcedente() {
        assertThat(Overcrowding.by(35, 30)).isEqualTo(5);
    }

    @Test
    @DisplayName("by: inscriptos igual a la capacidad devuelve 0")
    void igualDevuelveCero() {
        assertThat(Overcrowding.by(30, 30)).isZero();
    }

    @Test
    @DisplayName("by: inscriptos por debajo de la capacidad devuelve 0 (nunca negativo)")
    void porDebajoDevuelveCero() {
        assertThat(Overcrowding.by(10, 30)).isZero();
    }

    @Test
    @DisplayName("by: enrolled null devuelve null")
    void enrolledNullDevuelveNull() {
        assertThat(Overcrowding.by(null, 30)).isNull();
    }

    @Test
    @DisplayName("by: capacity null devuelve null")
    void capacityNullDevuelveNull() {
        assertThat(Overcrowding.by(30, null)).isNull();
    }
}
