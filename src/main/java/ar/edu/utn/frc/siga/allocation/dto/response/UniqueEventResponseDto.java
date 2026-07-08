package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.EventType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalTime;

@Value
@Builder
public class UniqueEventResponseDto implements AcademicEventResponseDto {
    Long id;
    EventType type;
    Integer enrolled;
    LocalTime startTime;
    long durationMinutes;
    LocalDate date;
    String description;
}