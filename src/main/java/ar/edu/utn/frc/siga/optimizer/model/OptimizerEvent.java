package ar.edu.utn.frc.siga.optimizer.model;

import ar.edu.utn.frc.siga.common.util.TimeSpan;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@NamedInterface("api")
public record OptimizerEvent(String planningId, String commissionKey, int enrolled,
                          LocalTime startTime, LocalTime endTime,
                          Set<LocalDate> occurrenceDates) implements TimeSpan {
}
