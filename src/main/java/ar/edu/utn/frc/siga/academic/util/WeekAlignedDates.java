package ar.edu.utn.frc.siga.academic.util;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class WeekAlignedDates {

    private WeekAlignedDates() {
    }

    /**
     * Misma posición ordinal de la semana dentro del mes + mismo día de semana, en {@code targetYear}:
     * si la fecha cae en el 2.º lunes de marzo, devuelve el 2.º lunes de marzo del año pedido. El
     * último caso (5.ª semana inexistente) se ajusta a la última ocurrencia de ese día en el mes.
     */
    public static LocalDate sameWeekOfMonth(LocalDate date, int targetYear) {
        int ordinal = (date.getDayOfMonth() - 1) / 7 + 1;
        LocalDate firstOfMonth = LocalDate.of(targetYear, date.getMonth(), 1);
        LocalDate aligned = firstOfMonth.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, date.getDayOfWeek()));
        if (aligned.getMonth() != date.getMonth()) {
            aligned = firstOfMonth.with(TemporalAdjusters.lastInMonth(date.getDayOfWeek()));
        }
        return aligned;
    }
}
