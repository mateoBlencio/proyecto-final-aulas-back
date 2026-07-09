package ar.edu.utn.frc.siga.solver.model;

import org.springframework.modulith.NamedInterface;

import java.util.List;

/**
 * Salida del solver: identificador para recuperar/confirmar la preview y las
 * asignaciones evento→aula. Sin adornos; allocation le da forma al cliente.
 */
@NamedInterface("api")
public record SolverPreview(String previewId, List<SolverAllocation> allocations) {
}