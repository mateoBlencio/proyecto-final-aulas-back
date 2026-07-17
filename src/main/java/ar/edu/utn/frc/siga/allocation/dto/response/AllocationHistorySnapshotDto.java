package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;

import java.time.LocalDateTime;

/**
 * Snapshot histórico de una {@code Allocation} en una revisión de auditoría: qué aula tenía
 * la ocurrencia en ese momento y con qué origen. {@code classroomId} plano sin resolver contra
 * el catálogo actual (el aula pudo renombrarse desde entonces).
 */
public record AllocationHistorySnapshotDto(
        Long id,
        Long occurrenceId,
        Integer classroomId,
        AllocationSource source,
        LocalDateTime createdAt,
        String observation
) {
}
