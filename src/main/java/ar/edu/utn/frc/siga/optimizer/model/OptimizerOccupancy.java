package ar.edu.utn.frc.siga.optimizer.model;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;

@NamedInterface("api")
public record OptimizerOccupancy(Integer classroomId, LocalDate date, LocalTime startTime, LocalTime endTime) {
}
