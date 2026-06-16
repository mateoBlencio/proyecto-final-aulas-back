package ar.edu.utn.frc.classroom_allocation.solver.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class UniqueEvent extends Event {

    LocalDate date;
    // EventType type;

    @Builder
    public UniqueEvent(String id, int enrolled, LocalTime startTime, Duration duration, LocalDate date) {
        super(id, enrolled, startTime, duration);
        this.date = date;
    }

    // Ocurre una única vez
    @Override
    public List<LocalDate> occurrences() {
        return List.of(date);
    }
}
