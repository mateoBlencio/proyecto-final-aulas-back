package ar.edu.utn.frc.classroom_allocation.allocation.mapper;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Allocation;
import org.springframework.stereotype.Component;

@Component
public class AllocationMapper {

    public AllocationResponseDto toDto(Allocation allocation) {
        return AllocationResponseDto.builder()
                .id(allocation.getId())
                .occurrenceId(allocation.getOccurrence().getId())
                .eventId(allocation.getOccurrence().getEvent().getId())
                .occurrenceDate(allocation.getOccurrence().getDate())
                .startTime(allocation.getOccurrence().startTime())
                .endTime(allocation.getOccurrence().endTime())
                .classroomId(allocation.getClassroom().getId())
                .classroomNumber(allocation.getClassroom().getRoomNumber())
                .assignedBy(allocation.getAssignedBy())
                .createdAt(allocation.getCreatedAt())
                .observation(allocation.getObservation())
                .build();
    }
}
