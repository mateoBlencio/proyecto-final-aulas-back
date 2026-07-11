package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ConfirmAutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ConfirmAutoPreviewResponseDto;
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

    /**
     * Confirma un preview de asignación automática: aplica de forma atómica la propuesta
     * FINAL ajustada por el usuario ({@code request.allocations()}), re-validando todo
     * contra el estado actual de la BD antes de escribir nada. Eventos con
     * {@code classroomId == null} quedan en {@code skippedEventIds} (revisión manual, no
     * se aplican). {@code source = AUTOMATIC} se estampa siempre dentro del servicio.
     * Invalida el preview al finalizar: un re-confirm sobre el mismo previewId da 410.
     * 410 si el preview expiró; 409 si hay duplicados, algún eventId no pertenece al
     * preview, un aula no existe/no está disponible, o la propuesta genera una
     * superposición nueva contra BD o entre sí.
     */
    ConfirmAutoPreviewResponseDto confirm(String previewId, ConfirmAutoPreviewRequestDto request);
}
