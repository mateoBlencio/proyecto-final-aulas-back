package PF.classroom_allocation.solver.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Asignación fija evento-aula que el solver debe respetar")
public class PinnedAssignmentDto {

    @NotBlank
    @Schema(description = "ID del evento a fijar", requiredMode = Schema.RequiredMode.REQUIRED)
    String eventId;

    @NotBlank
    @Schema(description = "ID del aula asignada", requiredMode = Schema.RequiredMode.REQUIRED)
    String classroomId;
}
