package ar.edu.utn.frc.siga.solver.model;

import org.springframework.modulith.NamedInterface;

/**
 * Resultado de asignación de un evento: aula elegida por el solver
 * (classroomId null = sin aula disponible sin conflicto).
 */
@NamedInterface("api")
public record SolverAllocation(String eventId, Integer classroomId) {
}