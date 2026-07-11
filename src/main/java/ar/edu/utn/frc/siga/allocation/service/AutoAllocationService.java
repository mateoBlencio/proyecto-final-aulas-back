package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ValidateMoveResponseDto;

/**
 * Orquesta la asignación automática: carga los eventos, junta las aulas disponibles y
 * la ocupación existente, y delega la optimización en el solver (motor puro). El solver
 * nunca lee allocations; la ocupación se le inyecta desde acá.
 */
public interface AutoAllocationService {

    /**
     * Genera una preview con el solver a partir de los eventos indicados (sin aula, o ya
     * asignados para re-resolver sobrecupo/superposición) y la compone con los datos que
     * necesita el calendario del front.
     */
    AutoPreviewResponseDto autoPreview(AutoPreviewRequestDto request);

    /** Recupera una preview generada previamente y la recompone contra el estado actual de la BD. */
    AutoPreviewResponseDto getPreview(String previewId);

    /**
     * Valida si mover el bloque de {@code request.eventId()} al aula
     * {@code request.classroomId()} genera una superposición nueva, contra lo firme de
     * BD y contra el resto de la propuesta ajustada que viaja en {@code currentAllocations}.
     * Responde siempre (nunca 409 por conflicto): el conflicto es un resultado esperado
     * de la interacción de arrastre y viaja en el body ({@code valid=false} + conflicts).
     * 410 si el preview expiró; 409 si {@code eventId} o algún elemento de
     * {@code currentAllocations} no pertenece al preview.
     */
    ValidateMoveResponseDto validateMove(String previewId, ValidateMoveRequestDto request);
}
