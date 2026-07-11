package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;

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
}
