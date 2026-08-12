package ar.edu.utn.frc.siga.preview.dto.response;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Resultado de confirmar un preview automático: las asignaciones efectivamente
 * aplicadas y los eventos que quedaron sin aula (revisión manual, no se aplicaron).
 */
@Schema(description = "Resultado de la confirmación del preview automático")
public record ConfirmPreviewResponseDto(
        List<AllocationResponseDto> applied,
        List<Long> skippedEventIds
) {
}
