package ar.edu.utn.frc.siga.preview.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Confirmación de un preview de asignación automática: lista FINAL de asignaciones tal
 * como quedó tras los ajustes del usuario en el front (drag-and-drop de aula incluido).
 * El backend re-valida todo contra la BD actual antes de aplicar nada.
 */
@Schema(description = "Confirmación del preview automático con la propuesta final ajustada")
public record ConfirmPreviewRequestDto(
        @NotEmpty
        @Valid
        @Schema(description = "Asignación final por evento", requiredMode = Schema.RequiredMode.REQUIRED)
        List<PreviewAllocationDto> allocations
) {
}
