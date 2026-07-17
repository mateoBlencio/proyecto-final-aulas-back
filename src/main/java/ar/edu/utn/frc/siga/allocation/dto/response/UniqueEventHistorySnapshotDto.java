package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.EventType;

import java.time.LocalDate;
import java.time.LocalTime;

/** Snapshot histórico de un {@code UniqueEvent}: estado del evento único en esa revisión. */
public record UniqueEventHistorySnapshotDto(
        Long id,
        EventType type,
        Integer enrolled,
        LocalTime startTime,
        long durationMinutes,
        LocalDate date,
        String description
) implements EventHistorySnapshotDto {
}
