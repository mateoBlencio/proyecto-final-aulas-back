package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Motivo, obligatorio solo si la sugerencia cubre menos aulas que las pedidas")
public record ConfirmRoomRequestSuggestionDto(@Size(max = 255) String reason) {
}
