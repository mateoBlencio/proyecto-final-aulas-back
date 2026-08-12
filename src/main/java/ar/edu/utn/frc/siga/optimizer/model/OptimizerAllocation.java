package ar.edu.utn.frc.siga.optimizer.model;

import org.springframework.modulith.NamedInterface;

/**
 * Resultado de asignación de un evento: aula elegida por el solver
 * (classroomId null = sin aula disponible sin conflicto).
 */
@NamedInterface("api")
public record OptimizerAllocation(String eventId, Integer classroomId) {
}
