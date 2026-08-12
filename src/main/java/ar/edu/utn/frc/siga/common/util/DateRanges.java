package ar.edu.utn.frc.siga.common.util;

import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;

import java.time.LocalDate;

public final class DateRanges {

    private DateRanges() {
    }

    public static LocalDate defaultFrom(LocalDate from) {
        return from != null ? from : LocalDate.now();
    }

    public static void requireNotBefore(LocalDate to, LocalDate from) {
        if (to != null && to.isBefore(from)) {
            throw new InvalidDateRangeException(
                    "'to' (" + to + ") no puede ser anterior a 'from' (" + from + ")");
        }
    }
}
