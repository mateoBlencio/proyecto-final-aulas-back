package ar.edu.utn.frc.siga.roomrequest.dto;

import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link RoomRequestItemFilter#of} resuelve el piso de fecha antes de llegar a la Specification. */
@DisplayName("RoomRequestItemFilter.of")
class RoomRequestItemFilterTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Test
    @DisplayName("includePast=false y dateFrom pasado: se pisa con hoy")
    void includePastFalsePastDateFromIsFlooredToToday() {
        RoomRequestItemFilter filter = RoomRequestItemFilter.of(
                null, null, null, null, TODAY.minusDays(30), null, false);

        assertThat(filter.dateFrom()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("includePast=false y dateFrom null: también cae en hoy")
    void includePastFalseNullDateFromDefaultsToToday() {
        RoomRequestItemFilter filter = RoomRequestItemFilter.of(null, null, null, null, null, null, false);

        assertThat(filter.dateFrom()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("includePast=false y dateFrom futuro: se respeta, no se acerca a hoy")
    void includePastFalseFutureDateFromIsKept() {
        LocalDate future = TODAY.plusDays(10);

        RoomRequestItemFilter filter = RoomRequestItemFilter.of(null, null, null, null, future, null, false);

        assertThat(filter.dateFrom()).isEqualTo(future);
    }

    @Test
    @DisplayName("includePast=true y dateFrom pasado: se respeta tal cual")
    void includePastTrueKeepsPastDateFrom() {
        LocalDate past = TODAY.minusDays(30);

        RoomRequestItemFilter filter = RoomRequestItemFilter.of(null, null, null, null, past, null, true);

        assertThat(filter.dateFrom()).isEqualTo(past);
    }

    @Test
    @DisplayName("includePast=true y dateFrom null: no hay piso, queda null")
    void includePastTrueNullDateFromStaysNull() {
        RoomRequestItemFilter filter = RoomRequestItemFilter.of(null, null, null, null, null, null, true);

        assertThat(filter.dateFrom()).isNull();
    }

    @Test
    @DisplayName("dateTo anterior al dateFrom efectivo: InvalidDateRangeException")
    void dateToBeforeEffectiveDateFromThrows() {
        assertThatThrownBy(() -> RoomRequestItemFilter.of(
                null, null, null, null, TODAY, TODAY.minusDays(1), false))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    @DisplayName("dateTo igual al dateFrom efectivo: no rompe (rango de un solo día)")
    void dateToEqualToEffectiveDateFromDoesNotThrow() {
        RoomRequestItemFilter filter = RoomRequestItemFilter.of(null, null, null, null, TODAY, TODAY, false);

        assertThat(filter.dateTo()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("dateTo sin dateFrom efectivo (includePast=true, dateFrom null): no valida nada")
    void dateToWithoutEffectiveDateFromDoesNotThrow() {
        LocalDate to = TODAY.plusDays(5);

        RoomRequestItemFilter filter = RoomRequestItemFilter.of(null, null, null, null, null, to, true);

        assertThat(filter.dateFrom()).isNull();
        assertThat(filter.dateTo()).isEqualTo(to);
    }
}
