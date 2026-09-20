package ar.edu.utn.frc.siga.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimeRanges")
class TimeRangesTest {

    @Test
    @DisplayName("overlaps: franjas que se pisan → true")
    void franjasQueSePisan() {
        assertThat(TimeRanges.overlaps(
                LocalTime.of(8, 0), LocalTime.of(10, 0),
                LocalTime.of(9, 0), LocalTime.of(11, 0))).isTrue();
    }

    @Test
    @DisplayName("overlaps: una contiene a la otra → true")
    void unaContieneALaOtra() {
        assertThat(TimeRanges.overlaps(
                LocalTime.of(8, 0), LocalTime.of(12, 0),
                LocalTime.of(9, 0), LocalTime.of(10, 0))).isTrue();
    }

    @Test
    @DisplayName("overlaps: franjas adyacentes (fin de una = inicio de la otra) → false")
    void franjasAdyacentesNoSolapan() {
        assertThat(TimeRanges.overlaps(
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(10, 0))).isFalse();
    }

    @Test
    @DisplayName("overlaps: franjas separadas → false")
    void franjasSeparadasNoSolapan() {
        assertThat(TimeRanges.overlaps(
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                LocalTime.of(10, 0), LocalTime.of(11, 0))).isFalse();
    }

    @Test
    @DisplayName("overlapMinutes: solape parcial devuelve los minutos pisados")
    void overlapMinutesSolapeParcial() {
        assertThat(TimeRanges.overlapMinutes(
                LocalTime.of(9, 0), LocalTime.of(10, 30),
                LocalTime.of(10, 0), LocalTime.of(11, 0))).isEqualTo(30L);
    }

    @Test
    @DisplayName("overlapMinutes: una franja contiene a la otra por completo")
    void overlapMinutesContencionTotal() {
        assertThat(TimeRanges.overlapMinutes(
                LocalTime.of(9, 0), LocalTime.of(12, 0),
                LocalTime.of(10, 0), LocalTime.of(10, 30))).isEqualTo(30L);
    }

    @Test
    @DisplayName("overlapMinutes: franjas adyacentes → 0")
    void overlapMinutesAdyacencia() {
        assertThat(TimeRanges.overlapMinutes(
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                LocalTime.of(10, 0), LocalTime.of(11, 0))).isZero();
    }

    @Test
    @DisplayName("overlapMinutes: franjas disjuntas → 0")
    void overlapMinutesDisjuntas() {
        assertThat(TimeRanges.overlapMinutes(
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                LocalTime.of(10, 0), LocalTime.of(11, 0))).isZero();
    }

    @Test
    @DisplayName("overlapMinutes: ejemplo del usuario, 40 minutos es el margen exacto y 41 ya lo supera")
    void overlapMinutesEjemploDelUsuario() {
        assertThat(TimeRanges.overlapMinutes(
                LocalTime.of(8, 0), LocalTime.of(10, 0),
                LocalTime.of(9, 20), LocalTime.of(11, 0))).isEqualTo(40L);

        assertThat(TimeRanges.overlapMinutes(
                LocalTime.of(8, 0), LocalTime.of(10, 0),
                LocalTime.of(9, 19), LocalTime.of(11, 0))).isEqualTo(41L);
    }
}
