package ar.edu.utn.frc.classroom_allocation.solver.dto.response;

import ar.edu.utn.frc.classroom_allocation.solver.model.AllocationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
@Schema(description = "Resultado del preview de asignación de aulas")
public class AllocationPreviewResponseDto {

    @Schema(description = "Identificador único del preview generado")
    String previewId;

    @Schema(description = "Timestamp del snapshot de entrada utilizado")
    Instant snapshotTimestamp;

    @Schema(description = "Timestamp de generación del resultado")
    Instant generatedAt;

    @Schema(description = "Estado general de la asignación")
    AllocationStatus status;

    @Schema(description = "Resumen estadístico de la asignación")
    AllocationSummaryDto summary;

    @Schema(description = "Lista de asignaciones evento-aula")
    List<AssignmentResultDto> assignments;

    @Schema(description = "Advertencias generadas durante la asignación")
    List<WarningDto> warnings;
}
