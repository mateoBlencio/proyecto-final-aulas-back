package ar.edu.utn.frc.siga.common.util;

import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DateRanges")
class DateRangesTest {

    @Test
    @DisplayName("defaultFrom: null devuelve hoy")
    void defaultFromNullDevuelveHoy() {
        assertThat(DateRanges.defaultFrom(null)).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("defaultFrom: valor presente se devuelve tal cual")
    void defaultFromPresenteSeDevuelveTalCual() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        assertThat(DateRanges.defaultFrom(date)).isEqualTo(date);
    }

    @Test
    @DisplayName("requireNotBefore: 'to' anterior a 'from' lanza InvalidDateRangeException")
    void requireNotBeforeToAnteriorLanza() {
        LocalDate from = LocalDate.of(2026, 3, 10);
        LocalDate to = LocalDate.of(2026, 3, 1);

        assertThatThrownBy(() -> DateRanges.requireNotBefore(to, from))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    @DisplayName("requireNotBefore: 'to' igual a 'from' no lanza (límite inclusive)")
    void requireNotBeforeToIgualNoLanza() {
        LocalDate from = LocalDate.of(2026, 3, 10);

        assertThatCode(() -> DateRanges.requireNotBefore(from, from)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireNotBefore: 'to' null no lanza (rango abierto)")
    void requireNotBeforeToNullNoLanza() {
        assertThatCode(() -> DateRanges.requireNotBefore(null, LocalDate.now())).doesNotThrowAnyException();
    }
}
