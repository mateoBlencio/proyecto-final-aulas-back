package PF.classroom_allocation.solver.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Métricas numéricas de la calidad de ocupación del aula")
public class QualityDetailDto {

    @Schema(description = "Alumnos que exceden la capacidad del aula (0 si no hay superpoblación)")
    int overcrowding;

    @Schema(description = "Capacidad sobrante en alumnos")
    int unusedCapacity;

    @Schema(description = "Ratio de ocupación (alumnos / capacidad). Valor ideal: entre 0.7 y 1.0")
    double occupancyRatio;
}
