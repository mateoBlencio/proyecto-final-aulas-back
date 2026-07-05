package ar.edu.utn.frc.siga.solver.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Advertencia generada durante el proceso de asignación")
public class WarningDto {

    @Schema(description = "Código de advertencia", example = "UNASSIGNED_EVENT")
    String code;

    @Schema(description = "ID del evento relacionado con la advertencia")
    String eventId;

    @Schema(description = "Mensaje descriptivo de la advertencia")
    String message;
}
