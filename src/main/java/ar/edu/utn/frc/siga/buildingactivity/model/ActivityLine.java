package ar.edu.utn.frc.siga.buildingactivity.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record ActivityLine(Long occurrenceId, String eventLabel, LocalDate date, LocalTime startTime,
                           LocalTime endTime, ClassroomRef fromClassroom, ClassroomRef toClassroom) {
}
