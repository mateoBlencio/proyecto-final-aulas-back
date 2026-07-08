package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.model.EventType;
import lombok.Builder;
import lombok.Value;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Value
@Builder
public class RecurringEventResponseDto implements AcademicEventResponseDto {
    Long id;
    EventType type;
    Integer enrolled;
    LocalTime startTime;
    long durationMinutes;
    DayOfWeek dayOfWeek;
    LocalDate startDate;
    LocalDate endDate;
    SubjectResponseDto subject;
    CommissionResponseDto commission;
}