package ar.edu.utn.frc.siga.allocation.events.dto.request;

import ar.edu.utn.frc.siga.allocation.events.model.UniqueEventKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Pedido de alta de un evento único (mesa de examen, parcial, trabajo práctico): ocurre una
 * sola vez en {@code date}. {@code subjectId} es obligatorio salvo para {@code eventType=OTRO};
 * {@code commissionId} nunca es obligatorio por sí solo, pero no puede venir sin {@code subjectId}.
 * {@code description} es la descripción propia del evento (texto libre).
 *
 * <p>No incluye aula: la asignación es responsabilidad de {@code allocation}
 * (ver {@code UniqueEventAllocationService#createUniqueEvent}).
 */
public record CreateUniqueEventRequestDto(
        @NotNull UniqueEventKind eventType,
        Long subjectId,
        Long commissionId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @Min(1) int durationMinutes,
        @NotNull @Min(1) Integer enrolled,
        String description
) {}
