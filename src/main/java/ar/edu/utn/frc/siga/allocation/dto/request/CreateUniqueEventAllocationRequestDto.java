package ar.edu.utn.frc.siga.allocation.dto.request;

import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Pedido de alta de un evento único con aula: crea el evento, su única occurrence y le
 * asigna {@code classroomId} en la misma operación (atómica) — si el aula no está disponible
 * o hay solapamiento, no se crea el evento. {@code subjectId} es obligatorio salvo para
 * {@code eventType=OTRO}; {@code commissionId} nunca es obligatorio por sí solo, pero no
 * puede venir sin {@code subjectId}.
 */
public record CreateUniqueEventAllocationRequestDto(
        @NotNull UniqueEventKind eventType,
        Long subjectId,
        Long commissionId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @Min(1) int durationMinutes,
        @NotNull @Min(1) Integer enrolled,
        @NotNull Integer classroomId,
        String description
) {}
