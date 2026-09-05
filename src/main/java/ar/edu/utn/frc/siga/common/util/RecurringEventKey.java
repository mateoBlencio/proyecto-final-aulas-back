package ar.edu.utn.frc.siga.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record RecurringEventKey(Long subjectId, Long commissionId, DayOfWeek dayOfWeek, LocalTime startTime,
        LocalDate startDate, LocalDate endDate) {
}
