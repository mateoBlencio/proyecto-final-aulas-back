package ar.edu.utn.frc.siga.roomrequest.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ClassSlotDto(
        Long recurringEventId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {}
