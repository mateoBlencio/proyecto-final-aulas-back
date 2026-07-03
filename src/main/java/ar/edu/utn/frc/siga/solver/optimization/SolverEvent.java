package ar.edu.utn.frc.siga.solver.optimization;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record SolverEvent(String planningId, int enrolled, LocalTime startTime, LocalTime endTime,
                          List<LocalDate> occurrenceDates) {
}
