package ar.edu.utn.frc.classroom_allocation.allocation.dto.response;

import ar.edu.utn.frc.classroom_allocation.allocation.model.EventType;
import lombok.Builder;
import lombok.Value;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Value
@Builder
public class AcademicEventResponseDto {
    Long id;
    EventType type;
    Integer enrolled;
    LocalTime startTime;
    long durationMinutes;
    // Recurring
    DayOfWeek dayOfWeek;
    LocalDate startDate;
    LocalDate endDate;
    String subject;
    String section;
    // Unique
    LocalDate date;
    String description;
}
