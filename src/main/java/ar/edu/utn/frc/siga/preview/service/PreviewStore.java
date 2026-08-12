package ar.edu.utn.frc.siga.preview.service;

import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;

import java.util.Optional;

/**
 * Guarda previews del solver para recuperarlas/confirmarlas. In-memory con TTL
 * (single-instance): el TTL acota la obsolescencia, no es eviction por memoria.
 */
public interface PreviewStore {

    void save(OptimizationResult preview);

    Optional<OptimizationResult> get(String previewId);

    /** Elimina una preview (p. ej. tras confirmarla): protege contra doble aplicación. */
    void remove(String previewId);
}
