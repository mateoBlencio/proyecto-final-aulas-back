package ar.edu.utn.frc.siga.solver.dto.request;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Solicitud de asignación de aulas")
public class AllocationRequestDto {

    @NotEmpty
    @Valid
    @Schema(description = "Lista de eventos académicos a asignar (mínimo 1)", requiredMode = Schema.RequiredMode.REQUIRED)
    List<EventRequestDto> events;

    @NotEmpty
    @Valid
    @Schema(description = "Lista de aulas disponibles (mínimo 1)", requiredMode = Schema.RequiredMode.REQUIRED)
    List<ClassroomResponseDTO> classrooms;

    @Valid
    @Schema(description = "Parámetros opcionales del solver")
    AllocationParametersDto parameters;
}
