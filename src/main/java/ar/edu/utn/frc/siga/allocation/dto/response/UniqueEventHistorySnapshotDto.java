package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.EventType;
import ar.edu.utn.frc.siga.allocation.model.UniqueEventKind;

import java.time.LocalDate;
import java.time.LocalTime;

/** Snapshot histórico de un {@code UniqueEvent}: estado del evento único en esa revisión, con IDs planos de materia/comisión. */
public record UniqueEventHistorySnapshotDto(
        Long id,
        EventType type,
        Integer enrolled,
        LocalTime startTime,
        long durationMinutes,
        LocalDate date,
        String description,
        UniqueEventKind eventType,
        Long subjectId,
        Long commissionId
) implements EventHistorySnapshotDto {
}
