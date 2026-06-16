package ar.edu.utn.frc.classroom_allocation.solver.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class RecurringEvent extends Event {

    DayOfWeek dayOfWeek;
    LocalDate startDate;
    LocalDate endDate;
    String    subject;
    String    section;

    @Builder
    public RecurringEvent(String id, int enrolled, LocalTime startTime, Duration duration,
                          DayOfWeek dayOfWeek, LocalDate startDate, LocalDate endDate,
                          String subject, String section) {
        super(id, enrolled, startTime, duration);
        this.dayOfWeek = dayOfWeek;
        this.startDate = startDate;
        this.endDate   = endDate;
        this.subject   = subject;
        this.section   = section;
    }

    @Override
    public List<LocalDate> occurrences() {
        return startDate.datesUntil(endDate.plusDays(1))
                .filter(d -> d.getDayOfWeek() == dayOfWeek)
                .toList();
    }
}
