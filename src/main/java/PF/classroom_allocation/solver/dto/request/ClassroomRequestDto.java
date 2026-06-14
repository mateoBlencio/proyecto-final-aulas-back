package PF.classroom_allocation.solver.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Aula disponible para asignación")
public class ClassroomRequestDto {

    @NotBlank
    @Schema(description = "Identificador único del aula", example = "AULA-101", requiredMode = Schema.RequiredMode.REQUIRED)
    String id;

    @NotBlank
    @Schema(description = "Nombre del aula", example = "Aula 101", requiredMode = Schema.RequiredMode.REQUIRED)
    String name;

    @NotBlank
    @Schema(description = "Edificio donde se encuentra el aula", example = "Pabellón A", requiredMode = Schema.RequiredMode.REQUIRED)
    String building;

    @Positive
    @Schema(description = "Superficie en metros cuadrados", example = "60.5", requiredMode = Schema.RequiredMode.REQUIRED)
    float capacityM2;
}
