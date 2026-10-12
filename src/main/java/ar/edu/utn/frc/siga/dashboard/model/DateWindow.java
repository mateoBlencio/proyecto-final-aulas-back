package ar.edu.utn.frc.siga.dashboard.model;

import java.time.LocalDate;

/** Inclusive date range that is measured on its own. */
public record DateWindow(LocalDate from, LocalDate to) {
}
