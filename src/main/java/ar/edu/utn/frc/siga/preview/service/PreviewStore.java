package ar.edu.utn.frc.siga.preview.service;

import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;

import java.util.Optional;

public interface PreviewStore {

    void save(OptimizationResult preview);

    Optional<OptimizationResult> get(String previewId);

    void remove(String previewId);
}
