package ar.edu.utn.frc.siga.sysacad.internal.write;

import java.time.LocalTime;

/** Hora de comienzo, duración y hora de fin tal como las muestra la vista de SysAcad. */
public record ScheduleTimes(LocalTime startTime, Integer durationMinutes, LocalTime endTime) {
}
