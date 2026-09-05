package ar.edu.utn.frc.siga.events.dto.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SysacadRecurringEventRefDto(
        Long eventId,
        Long subjectId,
        Long commissionId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalDate startDate,
        LocalDate endDate) {
}
