package ar.edu.utn.frc.siga.dashboard.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DashboardParameters")
class DashboardParametersTest {

    private static DashboardParameters withHours(LocalTime start, LocalTime end) {
        return new DashboardParameters(50, 40, start, end, 1);
    }

    @Test
    @DisplayName("endMinute con fin 00:00 devuelve 1440 (fin de día)")
    void endMinuteAtMidnightIsEndOfDay() {
        assertThat(withHours(LocalTime.of(8, 0), LocalTime.MIDNIGHT).endMinute()).isEqualTo(1440);
    }

    @Test
    @DisplayName("endMinute con fin 21:00 devuelve 1260")
    void endMinuteBeforeMidnightIsMinuteOfDay() {
        assertThat(withHours(LocalTime.of(8, 0), LocalTime.of(21, 0)).endMinute()).isEqualTo(1260);
    }

    @Test
    @DisplayName("endMinute con fin 23:59 devuelve 1439, un minuto antes del fin de día")
    void endMinuteOneMinuteBeforeMidnight() {
        assertThat(withHours(LocalTime.of(8, 0), LocalTime.of(23, 59)).endMinute()).isEqualTo(1439);
    }

    @Test
    @DisplayName("lastSlotHour con fin 00:00 devuelve 23")
    void lastSlotHourAtMidnightIs23() {
        assertThat(withHours(LocalTime.of(8, 0), LocalTime.MIDNIGHT).lastSlotHour()).isEqualTo(23);
    }

    @Test
    @DisplayName("la banda 08:00-00:00 dura 960 minutos")
    void bandUpToMidnightLasts960Minutes() {
        DashboardParameters params = withHours(LocalTime.of(8, 0), LocalTime.MIDNIGHT);

        assertThat(params.endMinute() - params.startMinute()).isEqualTo(960);
    }
}
