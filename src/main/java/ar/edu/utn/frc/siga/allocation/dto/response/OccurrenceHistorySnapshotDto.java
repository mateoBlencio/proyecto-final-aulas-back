package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;

import java.time.LocalDate;

/** Snapshot histórico de una {@code Occurrence} en una revisión de auditoría: fecha y estado en ese momento. */
public record OccurrenceHistorySnapshotDto(
        Long id,
        Long eventId,
        LocalDate date,
        OccurrenceStatus status
) {
}
