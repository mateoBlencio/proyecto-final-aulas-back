package ar.edu.utn.frc.siga.roomrequest.dto.response;

import java.time.LocalDate;

public record OriginalConflictDto(LocalDate date, ClassroomOptionDto originalClassroom, OccupiedByDto occupiedBy) {}
