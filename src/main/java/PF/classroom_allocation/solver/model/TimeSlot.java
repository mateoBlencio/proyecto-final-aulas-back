package PF.classroom_allocation.solver.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;

@Data
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TimeSlot {
    DayOfWeek day;
    LocalDateTime startTime;
    Duration duration;
}
