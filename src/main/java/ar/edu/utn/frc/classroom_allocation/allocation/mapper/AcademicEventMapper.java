package ar.edu.utn.frc.classroom_allocation.allocation.mapper;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.EventType;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.UniqueEvent;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

@Component
public class AcademicEventMapper {

    public AcademicEventResponseDto toDto(AcademicEvent event) {
        AcademicEvent realEvent = (AcademicEvent) Hibernate.unproxy(event);

        AcademicEventResponseDto.AcademicEventResponseDtoBuilder builder = AcademicEventResponseDto.builder()
                .id(realEvent.getId())
                .enrolled(realEvent.getEnrolled())
                .startTime(realEvent.getStartTime())
                .durationMinutes(realEvent.getDuration().toMinutes());

        if (realEvent instanceof RecurringEvent r) {
            var subject = r.getSubject();
            var commission = r.getCommission();
            var studyPlan = subject != null ? subject.getStudyPlan() : null;
            var specialty = studyPlan != null ? studyPlan.getSpecialty() : null;
            var period = commission != null ? commission.getAcademicPeriod() : null;
            builder.type(EventType.RECURRING)
                    .dayOfWeek(r.getDayOfWeek())
                    .startDate(r.getStartDate())
                    .endDate(r.getEndDate())
                    .subjectCode(subject != null ? subject.getCode() : null)
                    .subjectName(subject != null ? subject.getName() : null)
                    .subjectTerm(subject != null ? subject.getTerm() : null)
                    .studyPlanCode(studyPlan != null ? studyPlan.getPlanCode() : null)
                    .specialtyCode(specialty != null ? specialty.getSpecialtyCode() : null)
                    .specialtyName(specialty != null ? specialty.getName() : null)
                    .commissionCode(commission != null ? commission.getCourseCode() : null)
                    .commissionNumber(commission != null ? commission.getCommissionNumber() : null)
                    .yearLevel(commission != null ? commission.getYearLevel() : null)
                    .periodYear(period != null ? period.getYear() : null)
                    .periodSemester(period != null ? period.getSemester() : null);
        } else if (realEvent instanceof UniqueEvent u) {
            builder.type(EventType.UNIQUE_EVENT)
                    .date(u.getDate())
                    .description(u.getDescription());
        }

        return builder.build();
    }
}
