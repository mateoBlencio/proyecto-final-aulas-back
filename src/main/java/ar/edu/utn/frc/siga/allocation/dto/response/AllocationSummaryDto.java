package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.EventType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalTime;

@Value
@Builder
public class AllocationSummaryDto {
    Long id;
    EventType eventType;
    String subject;
    String section;
    Integer classroomId;
    String classroomName;
    Integer buildingId;
    LocalTime startTime;
    LocalTime endTime;
    Integer enrolled;
    Integer capacity;
}
