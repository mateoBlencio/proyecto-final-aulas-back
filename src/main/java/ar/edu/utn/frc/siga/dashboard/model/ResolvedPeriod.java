package ar.edu.utn.frc.siga.dashboard.model;

import java.time.LocalDate;

/**
 * Dates of a dashboard period. {@code from} to {@code to} is the range measured (period start up to today, or its
 * end if it already finished); {@code partial} is true when {@code to} is before {@code periodEnd}.
 * {@code semester} is 0 for the academic year and null for the month.
 */
public record ResolvedPeriod(DashboardPeriodType type, int year, Integer semester, LocalDate periodStart,
                             LocalDate periodEnd, LocalDate from, LocalDate to, boolean partial) {
}
