package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;

/**
 * Orquesta la asignación automática: carga los eventos, junta las aulas disponibles y
 * la ocupación existente, y delega la optimización en el solver (motor puro). El solver
 * nunca lee allocations; la ocupación se le inyecta desde acá.
 */
public interface AutoAllocationService {

    SolverPreview autoPreview(AutoPreviewRequestDto request);
}
