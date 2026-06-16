package ar.edu.utn.frc.classroom_allocation.solver.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Resumen estadístico del resultado de asignación")
public class AllocationSummaryDto {

    @Schema(description = "Total de eventos recibidos")
    int totalEvents;

    @Schema(description = "Eventos asignados exitosamente")
    int assigned;

    @Schema(description = "Eventos que no pudieron ser asignados")
    int unassigned;

    @Schema(description = "Puntuación hard del solver (0 = sin violaciones de restricciones duras)")
    int hardScore;

    @Schema(description = "Puntuación soft del solver (mayor es mejor)")
    int softScore;

    @Schema(description = "Tiempo de ejecución del solver en milisegundos")
    long solverDurationMs;
}
