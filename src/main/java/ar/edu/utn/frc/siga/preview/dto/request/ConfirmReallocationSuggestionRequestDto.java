package ar.edu.utn.frc.siga.preview.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Motivo del cambio de aula; obligatorio si la clase ya tenía aula y la sugerencia la cambia")
public record ConfirmReallocationSuggestionRequestDto(@Size(max = 500) String observation) {
}
