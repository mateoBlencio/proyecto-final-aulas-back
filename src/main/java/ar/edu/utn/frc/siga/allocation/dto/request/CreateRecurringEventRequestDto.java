package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.modulith.NamedInterface;

/**
 * Pedido de alta de un evento recurrente (clase regular): día de la semana, ventana de
 * dictado y materia/comisión que cursa. Genera una occurrence por semana entre
 * {@code startDate} y {@code endDate} (o un año desde {@code startDate} si es null).
 */
@NamedInterface("api")
public record CreateRecurringEventRequestDto(
        @NotNull @Min(1) Integer enrolled,
        @NotNull LocalTime startTime,
        @Min(1) int durationMinutes,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull Long subjectId,
        @NotNull Long commissionId
) {}
