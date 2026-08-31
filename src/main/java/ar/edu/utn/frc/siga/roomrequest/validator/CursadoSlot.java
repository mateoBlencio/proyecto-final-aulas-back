package ar.edu.utn.frc.siga.roomrequest.validator;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

/** Un día y horario de cursado de una comisión, tomado del evento recurrente de {@code events}. */
public record CursadoSlot(Long recurringEventId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

    public Duration duration() {
        return Duration.between(startTime, endTime);
    }
}
