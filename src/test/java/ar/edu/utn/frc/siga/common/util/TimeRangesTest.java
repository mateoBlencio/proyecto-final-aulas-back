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
}
