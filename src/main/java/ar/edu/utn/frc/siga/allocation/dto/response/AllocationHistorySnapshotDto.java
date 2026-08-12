package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;

import java.time.LocalDateTime;

public record AllocationHistorySnapshotDto(
        Long id,
        Long occurrenceId,
        Integer classroomId,
        AllocationSource source,
        LocalDateTime createdAt,
        String observation
) {
}
