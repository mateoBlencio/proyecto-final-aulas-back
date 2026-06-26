package ar.edu.utn.frc.classroom_allocation.allocation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

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
