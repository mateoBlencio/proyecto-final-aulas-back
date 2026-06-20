package ar.edu.utn.frc.classroom_allocation.solver.mapper;

import ar.edu.utn.frc.classroom_allocation.solver.dto.request.EventRequestDto;
import ar.edu.utn.frc.classroom_allocation.solver.dto.response.EventSummaryDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.UniqueEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class EventMapper {

    public List<AcademicEvent> toEvents(List<EventRequestDto> dtos) {
        return dtos.stream().map(this::toEvent).toList();
    }

    public AcademicEvent toEvent(EventRequestDto dto) {
        Duration duration = Duration.ofMinutes(dto.getDurationMinutes());
        return switch (dto.getType()) {
            case RECURRING -> RecurringEvent.builder()
                    .planningId(dto.getId())
                    .enrolled(dto.getEnrolled())
                    .startTime(dto.getStartTime())
                    .duration(duration)
                    .dayOfWeek(dto.getDayOfWeek())
                    .startDate(dto.getStartDate())
                    .endDate(dto.getEndDate())
                    .subject(dto.getSubject())
                    .section(dto.getSection())
                    .build();
            case UNIQUE -> UniqueEvent.builder()
                    .planningId(dto.getId())
                    .enrolled(dto.getEnrolled())
                    .startTime(dto.getStartTime())
                    .duration(duration)
                    .date(dto.getDate())
                    .build();
        };
    }

    public EventSummaryDto toSummary(EventRequestDto dto) {
        return EventSummaryDto.builder()
                .id(dto.getId())
                .type(dto.getType())
                .subject(dto.getSubject())
                .section(dto.getSection())
                .enrolled(dto.getEnrolled())
                .startTime(dto.getStartTime())
                .durationMinutes(dto.getDurationMinutes())
                .dayOfWeek(dto.getDayOfWeek())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .date(dto.getDate())
                .build();
    }
}
