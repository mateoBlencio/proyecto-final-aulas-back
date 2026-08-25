package ar.edu.utn.frc.siga.allocation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Conflicto de reasignación: ocurrencia que solapa con una asignación existente")
public record OccurrenceConflictDto(
        Long occurrenceId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long classroomId,
        Long conflictingEventId,
        Long conflictingAllocationId) {
}
