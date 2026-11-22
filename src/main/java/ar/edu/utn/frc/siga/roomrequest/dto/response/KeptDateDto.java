package ar.edu.utn.frc.siga.roomrequest.dto.response;

import java.time.LocalDate;

/** {@code originalClassroom} y {@code occupiedBy} son nulos si la fecha no tiene aula original guardada. */
public record KeptDateDto(LocalDate date, ClassroomOptionDto originalClassroom, OccupiedByDto occupiedBy) {}
