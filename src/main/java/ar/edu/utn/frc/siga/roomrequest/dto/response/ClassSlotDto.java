package ar.edu.utn.frc.siga.roomrequest.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Un día y horario de cursado de la comisión, para pintar los checkboxes de día del formulario. */
public record ClassSlotDto(
        Long recurringEventId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {}
