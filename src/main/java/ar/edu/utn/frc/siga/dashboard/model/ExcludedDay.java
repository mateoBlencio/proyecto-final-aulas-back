package ar.edu.utn.frc.siga.dashboard.model;

import java.time.LocalDate;

public record ExcludedDay(LocalDate date, ExcludedDayReason reason, String description) {
}
