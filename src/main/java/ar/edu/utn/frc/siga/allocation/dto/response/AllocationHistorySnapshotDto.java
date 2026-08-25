package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;

import java.time.Instant;

public record AllocationHistorySnapshotDto(
        Long id,
        Long occurrenceId,
        Long classroomId,
        AllocationSource source,
        Instant createdAt,
        String observation
) {
}
