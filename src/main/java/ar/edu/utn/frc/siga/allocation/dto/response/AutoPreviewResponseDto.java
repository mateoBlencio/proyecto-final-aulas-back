package ar.edu.utn.frc.siga.allocation.dto.response;

import java.util.List;

/**
 * Respuesta propia de allocation para el preview automático (deuda A5): reemplaza el
 * {@code SolverPreview} crudo del módulo solver por un DTO con los datos que el
 * calendario del front necesita para dibujar la propuesta. {@code allocations} son las
 * filas con aula propuesta; {@code unresolved} son los eventos que el solver no pudo
 * ubicar sin conflicto y quedan para revisión manual.
 */
public record AutoPreviewResponseDto(
        String previewId,
        List<ProposedAllocationDto> allocations,
        List<ProposedAllocationDto> unresolved
) {
}
