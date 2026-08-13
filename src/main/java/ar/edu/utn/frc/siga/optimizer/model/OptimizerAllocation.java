package ar.edu.utn.frc.siga.optimizer.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record OptimizerAllocation(String eventId, Integer classroomId) {
}
