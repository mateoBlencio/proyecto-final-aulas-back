package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.model.EventType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/** Vista de respuesta de un {@code RecurringEvent}: clase regular con día fijo de la semana y ventana de dictado. */
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
}
