package ar.edu.utn.frc.siga.preview.service;

import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;

/**
 * Orquesta la asignación automática: carga los eventos, junta las aulas disponibles y
 * la ocupación existente, y delega la optimización en el solver (motor puro). El solver
 * nunca lee allocations; la ocupación se le inyecta desde acá.
 */
public interface PreviewService {

    /**
     * Genera una preview con el solver a partir de los eventos indicados (sin aula, o ya
     * asignados para re-resolver sobrecupo/superposición) y la compone con los datos que
     * necesita el calendario del front.
     */
    PreviewResponseDto autoPreview(PreviewRequestDto request);

    /** Recupera una preview generada previamente y la recompone contra el estado actual de la BD. */
    PreviewResponseDto getPreview(String previewId);

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
    ConfirmPreviewResponseDto confirm(String previewId, ConfirmPreviewRequestDto request);
}
