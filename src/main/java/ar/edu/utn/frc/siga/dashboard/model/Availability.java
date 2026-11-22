package ar.edu.utn.frc.siga.dashboard.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Days that count as available (Monday to Saturday, no holiday, no recess) and the ones left out. */
public record Availability(Set<LocalDate> availableDays, List<ExcludedDay> excludedDays) {
}
