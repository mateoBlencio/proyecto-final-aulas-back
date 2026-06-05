package PF.classroom_allocation.solver.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Event {

    String id;
    int enrolled;
    LocalTime startTime;
    Duration duration;

    public LocalTime endTime() {
        return startTime.plus(duration);
    }

    public abstract List<LocalDate> occurrences();
}
