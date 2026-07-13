package ar.edu.utn.frc.siga.solver.model;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Evento para el solver. {@code commissionKey} agrupa eventos de la misma comisión
 * (ej. 1K1) para la preferencia soft de misma aula/edificio; null si no aplica
 * (o para las ocupaciones existentes sintéticas).
 */
@NamedInterface("api")
public record SolverEvent(String planningId, String commissionKey, int enrolled,
                          LocalTime startTime, LocalTime endTime,
                          Set<LocalDate> occurrenceDates) {
}
