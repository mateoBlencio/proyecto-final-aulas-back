package ar.edu.utn.frc.siga.solver.dto.response;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Resultado de asignación para un evento específico")
public class AssignmentResultDto {

    @Schema(description = "Resumen del evento asignado")
    EventSummaryDto event;

    @Schema(description = "Aula asignada (null si el evento quedó sin asignar)")
    ClassroomResponseDTO classroom;

    @Schema(description = "Calidad de la asignación: OPTIMAL, ACCEPTABLE, POOR, UNASSIGNED")
    AllocationQuality quality;

    @Schema(description = "Detalle numérico de la calidad de ocupación")
    QualityDetailDto qualityDetail;
}
