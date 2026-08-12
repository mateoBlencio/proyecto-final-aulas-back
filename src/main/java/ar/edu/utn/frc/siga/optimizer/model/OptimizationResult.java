package ar.edu.utn.frc.siga.optimizer.model;

import org.springframework.modulith.NamedInterface;

import java.util.List;

@NamedInterface("api")
public record OptimizationResult(String previewId, List<OptimizerAllocation> allocations) {
}
