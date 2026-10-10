package ar.edu.utn.frc.siga.dashboard.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Open room request item (NEW, DERIVED_TO_BUILDING or IN_EVALUATION), as the dashboard needs it. */
public record PendingItemRow(
        String status, Long derivedBuildingId, LocalDate date, LocalTime startTime,
        Instant createdAt, Instant derivedAt) {
}
