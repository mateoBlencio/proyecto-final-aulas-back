package ar.edu.utn.frc.siga.solver.optimization;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record SolverEvent(String planningId, int enrolled, LocalTime startTime, LocalTime endTime,
                          Set<LocalDate> occurrenceDates) {
}
