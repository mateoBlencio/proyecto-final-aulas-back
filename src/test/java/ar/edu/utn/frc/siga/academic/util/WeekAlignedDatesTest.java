package ar.edu.utn.frc.siga.academic.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WeekAlignedDates.sameWeekOfMonth")
class WeekAlignedDatesTest {

    @Test
    @DisplayName("2.º lunes de marzo → 2.º lunes de marzo del año pedido")
    void secondMondayOfMarch() {
        LocalDate source = LocalDate.of(2026, 3, 9);
        assertThat(source.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

        LocalDate aligned = WeekAlignedDates.sameWeekOfMonth(source, 2027);

        assertThat(aligned.getYear()).isEqualTo(2027);
        assertThat(aligned.getMonthValue()).isEqualTo(3);
        assertThat(aligned.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat((aligned.getDayOfMonth() - 1) / 7 + 1).isEqualTo(2);
    }

    @Test
    @DisplayName("5.ª ocurrencia inexistente en el mes destino → última de ese día")
    void fifthWeekFallsBackToLast() {
        LocalDate source = LocalDate.of(2026, 3, 30);
        assertThat((source.getDayOfMonth() - 1) / 7 + 1).isEqualTo(5);

        LocalDate aligned = WeekAlignedDates.sameWeekOfMonth(source, 2027);

        assertThat(aligned.getMonthValue()).isEqualTo(3);
        assertThat(aligned.getDayOfWeek()).isEqualTo(source.getDayOfWeek());
        assertThat(aligned.plusWeeks(1).getMonthValue()).isEqualTo(4);
    }
}
