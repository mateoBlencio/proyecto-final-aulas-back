package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import org.springframework.modulith.NamedInterface;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@NamedInterface("api")
public record RecurringEventResponseDto(
        Long id,
        EventType type,
        Integer enrolled,
        LocalTime startTime,
        long durationMinutes,
        DayOfWeek dayOfWeek,
        LocalDate startDate,
        LocalDate endDate,
        SubjectResponseDto subject,
        CommissionResponseDto commission
) implements AcademicEventResponseDto {

    public LocalTime endTime() {
        return startTime.plusMinutes(durationMinutes);
    }
}
