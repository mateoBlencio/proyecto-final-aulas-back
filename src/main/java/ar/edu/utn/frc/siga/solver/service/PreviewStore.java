package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.solver.model.SolverPreview;

import java.util.Optional;

/**
 * Guarda previews del solver para recuperarlas/confirmarlas. In-memory con TTL
 * (single-instance): el TTL acota la obsolescencia, no es eviction por memoria.
 */
public interface PreviewStore {

    void save(SolverPreview preview);

    Optional<SolverPreview> get(String previewId);

    /** Elimina una preview (p. ej. tras confirmarla): protege contra doble aplicación. */
    void remove(String previewId);
}
