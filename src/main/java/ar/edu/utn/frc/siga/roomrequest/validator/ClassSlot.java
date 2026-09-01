package ar.edu.utn.frc.siga.roomrequest.validator;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

public record ClassSlot(Long recurringEventId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

    public Duration duration() {
        return Duration.between(startTime, endTime);
    }
}
