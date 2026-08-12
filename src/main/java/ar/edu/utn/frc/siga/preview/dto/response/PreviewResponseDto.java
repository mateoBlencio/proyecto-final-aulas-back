package ar.edu.utn.frc.siga.preview.dto.response;

import java.util.List;

/**
 * Preview de asignación automática para el calendario del front. {@code allocations} son
 * las filas con aula propuesta; {@code unresolved} son los eventos que el solver no pudo
 * ubicar sin conflicto y quedan para revisión manual, con los solapes que explican el motivo.
 */
public record PreviewResponseDto(
        String previewId,
        List<PreviewItemDto> allocations,
        List<UnresolvedAllocationDto> unresolved
) {
}
