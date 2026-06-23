package ar.edu.utn.frc.classroom_allocation.allocation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateUniqueEventRequestDto(
        @NotNull @Min(1) Integer enrolled,
        @NotNull LocalTime startTime,
        @Min(1) int durationMinutes,
        @NotNull LocalDate date,
        String description
) {}
