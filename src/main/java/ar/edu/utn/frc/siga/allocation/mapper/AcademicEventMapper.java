package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.EventType;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

@Component
public class AcademicEventMapper {

    public AcademicEventResponseDto toDto(AcademicEvent event, SubjectResponseDto subject, CommissionResponseDto commission) {
        AcademicEvent realEvent = (AcademicEvent) Hibernate.unproxy(event);

        if (realEvent instanceof RecurringEvent r) {
            return RecurringEventResponseDto.builder()
                    .id(r.getId())
                    .type(EventType.RECURRING)
                    .enrolled(r.getEnrolled())
                    .startTime(r.getStartTime())
                    .durationMinutes(r.getDuration().toMinutes())
                    .dayOfWeek(r.getDayOfWeek())
                    .startDate(r.getStartDate())
                    .endDate(r.getEndDate())
                    .subject(subject)
                    .commission(commission)
                    .build();
        }

        UniqueEvent u = (UniqueEvent) realEvent;
        return UniqueEventResponseDto.builder()
                .id(u.getId())
                .type(EventType.UNIQUE_EVENT)
                .enrolled(u.getEnrolled())
                .startTime(u.getStartTime())
                .durationMinutes(u.getDuration().toMinutes())
                .date(u.getDate())
                .description(u.getDescription())
                .build();
    }
}