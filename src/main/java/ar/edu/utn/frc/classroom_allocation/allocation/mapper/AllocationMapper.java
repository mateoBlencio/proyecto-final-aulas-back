package ar.edu.utn.frc.classroom_allocation.allocation.mapper;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationSummaryDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Allocation;
import ar.edu.utn.frc.classroom_allocation.allocation.model.EventType;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Occurrence;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.space.mapper.ClassroomMapper;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AllocationMapper {

    private final AcademicEventMapper eventMapper;
    private final ClassroomMapper classroomMapper;

    public AllocationResponseDto toDto(Allocation allocation) {
        Occurrence occurrence = allocation.getOccurrence();

        OccurrenceResponseDto occurrenceDto = OccurrenceResponseDto.builder()
                .id(occurrence.getId())
                .eventId(occurrence.getEvent().getId())
                .date(occurrence.getDate())
                .status(occurrence.getStatus())
                .startTime(occurrence.startTime())
                .endTime(occurrence.endTime())
                .build();

        return AllocationResponseDto.builder()
                .id(allocation.getId())
                .source(allocation.getSource())
                .createdAt(allocation.getCreatedAt())
                .observation(allocation.getObservation())
                .occurrence(occurrenceDto)
                .event(eventMapper.toDto(occurrence.getEvent()))
                .classroom(classroomMapper.toResponseDto(allocation.getClassroom()))
                .build();
    }

    public AllocationSummaryDto toSummaryDto(Allocation allocation) {
        AcademicEvent event = (AcademicEvent) Hibernate.unproxy(allocation.getOccurrence().getEvent());

        String subject = null;
        String section = null;
        EventType eventType;

        if (event instanceof RecurringEvent r) {
            eventType = EventType.RECURRING;
            subject = r.getSubject() != null ? r.getSubject().getName() : null;
            section = r.getCommission() != null ? r.getCommission().getCourseCode() : null;
        } else {
            eventType = EventType.UNIQUE_EVENT;
        }

        var classroom = allocation.getClassroom();
        return AllocationSummaryDto.builder()
                .id(allocation.getId())
                .eventType(eventType)
                .subject(subject)
                .section(section)
                .classroomId(classroom.getId())
                .classroomName(classroom.getRoomNumber())
                .buildingId(classroom.getBuilding().getId())
                .startTime(event.getStartTime())
                .endTime(event.endTime())
                .enrolled(event.getEnrolled())
                .capacity(classroom.getCapacity())
                .build();
    }
}
