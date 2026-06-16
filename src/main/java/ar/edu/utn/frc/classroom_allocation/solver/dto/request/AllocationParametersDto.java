package ar.edu.utn.frc.classroom_allocation.solver.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Parámetros opcionales de configuración del solver")
public class AllocationParametersDto {

    @Min(value = 1, message = "timeLimitSeconds must be at least 1")
    @Max(value = 300, message = "timeLimitSeconds must be at most 300")
    @Schema(description = "Tiempo máximo de ejecución del solver en segundos (1-300)", example = "30", defaultValue = "30")
    int timeLimitSeconds = 30;

    @Schema(description = "Asignaciones fijadas que el solver no puede modificar")
    List<PinnedAssignmentDto> pinnedAssignments = List.of();

    @Schema(description = "IDs de aulas excluidas de la asignación")
    List<Integer> excludedClassroomIds = List.of();

    @Schema(description = "Nombres de edificios excluidos de la asignación")
    List<String> excludedBuildingNames = List.of();
}
