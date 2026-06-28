package ar.edu.utn.frc.classroom_allocation.allocation.dto.response;

import ar.edu.utn.frc.classroom_allocation.allocation.model.EventType;
import lombok.Builder;
import lombok.Value;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Value
@Builder
public class AcademicEventResponseDto {
    Long id;
    EventType type;
    Integer enrolled;
    LocalTime startTime;
    long durationMinutes;
    // Recurring
    DayOfWeek dayOfWeek;
    LocalDate startDate;
    LocalDate endDate;
    Integer subjectCode;
    String subjectName;
    String subjectTerm;
    Integer studyPlanCode;
    Integer specialtyCode;
    String specialtyName;
    String commissionCode;
    Integer commissionNumber;
    Integer yearLevel;
    Integer periodYear;
    Integer periodSemester;
    // Unique
    LocalDate date;
    String description;
}
