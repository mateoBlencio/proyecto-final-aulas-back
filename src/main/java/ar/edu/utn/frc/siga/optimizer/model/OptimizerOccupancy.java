package ar.edu.utn.frc.siga.optimizer.model;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Ocupación existente inyectada por allocation: un aula ya asignada en una fecha/hora.
 * El solver la modela como asignación pinned para no solapar los eventos nuevos.
 */
@NamedInterface("api")
public record OptimizerOccupancy(Integer classroomId, LocalDate date, LocalTime startTime, LocalTime endTime) {
}
