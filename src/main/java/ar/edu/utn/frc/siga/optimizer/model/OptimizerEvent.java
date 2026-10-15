package ar.edu.utn.frc.siga.optimizer.model;

import ar.edu.utn.frc.siga.common.util.TimeSpan;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@NamedInterface("api")
public record OptimizerEvent(String planningId, String commissionKey, int enrolled,
                          LocalTime startTime, LocalTime endTime,
                          Set<LocalDate> occurrenceDates, Set<Long> subjectIds, String groupKey,
                          boolean needsProjector, int computersNeeded) implements TimeSpan {

    public OptimizerEvent(String planningId, String commissionKey, int enrolled,
                          LocalTime startTime, LocalTime endTime,
                          Set<LocalDate> occurrenceDates, Set<Long> subjectIds, String groupKey) {
        this(planningId, commissionKey, enrolled, startTime, endTime, occurrenceDates, subjectIds, groupKey,
                false, 0);
    }

    public OptimizerEvent(String planningId, String commissionKey, int enrolled,
                          LocalTime startTime, LocalTime endTime, Set<LocalDate> occurrenceDates) {
        this(planningId, commissionKey, enrolled, startTime, endTime, occurrenceDates, Set.of(), null);
    }

    public OptimizerEvent(String planningId, String commissionKey, int enrolled,
                          LocalTime startTime, LocalTime endTime,
                          Set<LocalDate> occurrenceDates, Set<Long> subjectIds) {
        this(planningId, commissionKey, enrolled, startTime, endTime, occurrenceDates, subjectIds, null);
    }
}
