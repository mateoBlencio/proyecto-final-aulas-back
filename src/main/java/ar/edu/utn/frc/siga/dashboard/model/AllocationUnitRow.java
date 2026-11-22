package ar.edu.utn.frc.siga.dashboard.model;

import ar.edu.utn.frc.siga.common.util.TimeSpan;
import java.time.LocalDate;
import java.time.LocalTime;

/** Lightweight read model of one allocation: the unit the dashboard counts. */
public record AllocationUnitRow(
        Long allocationId, Long classroomId, Long buildingId, String source, LocalDate date,
        Long eventId, int startMinute, int endMinute, int enrolled, Integer capacity) implements TimeSpan {

    @Override
    public LocalTime startTime() {
        return LocalTime.ofSecondOfDay(startMinute * 60L);
    }

    @Override
    public LocalTime endTime() {
        return endMinute >= 1440 ? LocalTime.MAX : LocalTime.ofSecondOfDay(endMinute * 60L);
    }
}
