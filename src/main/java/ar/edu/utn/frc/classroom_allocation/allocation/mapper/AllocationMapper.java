package ar.edu.utn.frc.classroom_allocation.allocation.mapper;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Allocation;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.UniqueEvent;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import org.springframework.stereotype.Component;

@Component
public class AllocationMapper {

    public AllocationResponseDto toDto(Allocation allocation) {
        AcademicEvent event = allocation.getOccurrence().getEvent();
        Classroom classroom = allocation.getClassroom();

        String subject = event instanceof RecurringEvent r ? r.getSubject() : null;
        String section = event instanceof RecurringEvent r ? r.getSection() : null;
        String eventDescription = event instanceof UniqueEvent u ? u.getDescription() : null;

        return AllocationResponseDto.builder()
                .id(allocation.getId())
                .assignedBy(allocation.getAssignedBy())
                .createdAt(allocation.getCreatedAt())
                .observation(allocation.getObservation())
                .occurrenceId(allocation.getOccurrence().getId())
                .occurrenceDate(allocation.getOccurrence().getDate())
                .eventId(event.getId())
                .startTime(allocation.getOccurrence().startTime())
                .endTime(allocation.getOccurrence().endTime())
                .enrolled(event.getEnrolled())
                .subject(subject)
                .section(section)
                .eventDescription(eventDescription)
                .classroomId(classroom.getId())
                .classroomNumber(classroom.getRoomNumber())
                .floor(classroom.getFloor())
                .capacity(classroom.getCapacity())
                .buildingId(classroom.getBuilding().getId())
                .buildingName(classroom.getBuilding().getName())
                .classroomType(classroom.getClassroomType().getDescription())
                .build();
    }
}
