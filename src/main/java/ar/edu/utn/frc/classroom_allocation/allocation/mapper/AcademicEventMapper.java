package ar.edu.utn.frc.classroom_allocation.allocation.mapper;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.EventType;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.UniqueEvent;
import org.springframework.stereotype.Component;

@Component
public class AcademicEventMapper {

    public AcademicEventResponseDto toDto(AcademicEvent event) {
        AcademicEventResponseDto.AcademicEventResponseDtoBuilder builder = AcademicEventResponseDto.builder()
                .id(event.getId())
                .enrolled(event.getEnrolled())
                .startTime(event.getStartTime())
                .durationMinutes(event.getDuration().toMinutes());

        if (event instanceof RecurringEvent r) {
            builder.type(EventType.RECURRING)
                    .dayOfWeek(r.getDayOfWeek())
                    .startDate(r.getStartDate())
                    .endDate(r.getEndDate())
                    .subject(r.getSubject())
                    .section(r.getSection());
        } else if (event instanceof UniqueEvent u) {
            builder.type(EventType.UNIQUE_EVENT)
                    .date(u.getDate())
                    .description(u.getDescription());
        }

        return builder.build();
    }
}
