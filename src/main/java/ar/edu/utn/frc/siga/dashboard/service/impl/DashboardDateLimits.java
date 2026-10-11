package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/** Bounds on the dates a caller can ask for, so extreme values answer 400 instead of overflowing date arithmetic. */
final class DashboardDateLimits {

    static final int MAX_YEARS = 10;

    private DashboardDateLimits() {
    }

    static void requireWithinRange(LocalDate date, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today.minusYears(MAX_YEARS)) || date.isAfter(today.plusYears(MAX_YEARS))) {
            throw new InvalidDateRangeException("La fecha debe estar a menos de " + MAX_YEARS + " años de hoy");
        }
    }

    static void requireWithinRange(YearMonth month, Clock clock) {
        YearMonth current = YearMonth.now(clock);
        if (month.isBefore(current.minusYears(MAX_YEARS)) || month.isAfter(current.plusYears(MAX_YEARS))) {
            throw new InvalidDateRangeException("El mes debe estar a menos de " + MAX_YEARS + " años de hoy");
        }
    }

    static void requireWithinRange(int year, Clock clock) {
        int current = LocalDate.now(clock).getYear();
        if (year < current - MAX_YEARS || year > current + MAX_YEARS) {
            throw new InvalidDateRangeException("El año debe estar a menos de " + MAX_YEARS + " años de hoy");
        }
    }
}
