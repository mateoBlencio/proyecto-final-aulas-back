package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** Pedido de alta de un evento único (mesa de examen, parcial, trabajo práctico): ocurre una sola vez en {@code date}. */
public record CreateUniqueEventRequestDto(
        @NotNull @Min(1) Integer enrolled,
        @NotNull LocalTime startTime,
        @Min(1) int durationMinutes,
        @NotNull LocalDate date,
        String description
) {}
