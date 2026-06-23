package ar.edu.utn.frc.classroom_allocation.allocation.dto.response;

import ar.edu.utn.frc.classroom_allocation.allocation.model.AllocationSource;
import ar.edu.utn.frc.classroom_allocation.space.dto.response.ClassroomResponseDTO;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AllocationResponseDto {
    Long id;
    AllocationSource source;
    LocalDateTime createdAt;
    String observation;
    OccurrenceResponseDto occurrence;
    AcademicEventResponseDto event;
    ClassroomResponseDTO classroom;
}
