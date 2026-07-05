package ar.edu.utn.frc.siga.solver.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Asignación fija evento-aula que el solver debe respetar")
public class PinnedAssignmentDto {

    @NotBlank
    @Schema(description = "ID del evento a fijar", requiredMode = Schema.RequiredMode.REQUIRED)
    String eventId;

    @NotNull
    @Schema(description = "ID del aula asignada", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer classroomId;
}
