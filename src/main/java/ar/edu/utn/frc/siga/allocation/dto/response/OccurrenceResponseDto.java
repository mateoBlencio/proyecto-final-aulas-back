package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalTime;

@Value
@Builder
public class OccurrenceResponseDto {
    Long id;
    Long eventId;
    LocalDate date;
    OccurrenceStatus status;
    LocalTime startTime;
    LocalTime endTime;
}
