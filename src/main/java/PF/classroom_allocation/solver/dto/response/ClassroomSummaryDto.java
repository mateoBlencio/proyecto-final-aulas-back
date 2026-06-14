package PF.classroom_allocation.solver.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Resumen del aula asignada")
public class ClassroomSummaryDto {

    @Schema(description = "Identificador del aula")
    String id;

    @Schema(description = "Nombre del aula")
    String name;

    @Schema(description = "Edificio")
    String building;

    @Schema(description = "Superficie en metros cuadrados")
    float capacityM2;
}
