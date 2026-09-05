package ar.edu.utn.frc.siga.events.service.command;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SyncRecurringEventCommand(
        Long subjectId,
        Long commissionId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        int durationMinutes,
        int enrolled,
        LocalDate startDate,
        LocalDate endDate
) {}
