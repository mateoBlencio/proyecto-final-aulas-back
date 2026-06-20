package ar.edu.utn.frc.classroom_allocation.allocation.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Value
@Builder
public class AllocationResponseDto {
    Long id;
    Long occurrenceId;
    Long eventId;
    LocalDate occurrenceDate;
    LocalTime startTime;
    LocalTime endTime;
    Integer classroomId;
    String classroomNumber;
    String assignedBy;
    LocalDateTime createdAt;
    String observation;
}
