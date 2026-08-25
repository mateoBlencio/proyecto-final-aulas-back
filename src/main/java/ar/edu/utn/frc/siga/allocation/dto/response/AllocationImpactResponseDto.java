package ar.edu.utn.frc.siga.allocation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Vista previa del impacto de un pedido de asignación, sin escribir nada")
public record AllocationImpactResponseDto(
        int totalClasses,
        int movableClasses,
        int blockedClasses,
        List<ImpactOccurrenceDto> occurrences,
        List<ImpactConflictDto> conflicts) {
}
