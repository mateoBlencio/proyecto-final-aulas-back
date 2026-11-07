package ar.edu.utn.frc.siga.sysacad.internal.write;

import java.time.LocalDate;

/** Clase de SIGA alcanzada por un cambio directo, con el aula que tenía planificada. */
public record AffectedOccurrence(Long occurrenceId, LocalDate date, Long plannedClassroomId) {
}
