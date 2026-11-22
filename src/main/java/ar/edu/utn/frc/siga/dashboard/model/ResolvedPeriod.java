package ar.edu.utn.frc.siga.dashboard.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Dates of a dashboard period. {@code from} to {@code to} is the range shown (period start up to today, or its
 * end if it already finished); {@code partial} is true when {@code to} is before {@code periodEnd}.
 * {@code semester} is 0 for the academic year and null for LAST_YEARS, which sets {@code years} instead of
 * {@code year}. {@code windows} are the ranges actually measured: one, except in LAST_YEARS, where each academic
 * year is measured on its own and the days between two of them do not count.
 */
public record ResolvedPeriod(DashboardPeriodType type, Integer year, Integer semester, Integer years,
                             LocalDate periodStart, LocalDate periodEnd, LocalDate from, LocalDate to,
                             boolean partial, List<DateWindow> windows) {
}
