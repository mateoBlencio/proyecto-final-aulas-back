package ar.edu.utn.frc.siga.common.util;

import java.time.LocalDate;

/** Clave de agrupación para {@link Clashes}: mismo aula, misma fecha. */
public record RoomDate(Integer classroomId, LocalDate date) {
}
