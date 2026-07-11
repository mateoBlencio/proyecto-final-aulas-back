package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

import org.springframework.modulith.NamedInterface;

/**
 * Pedido de asignación manual en bloque para un evento recurrente: asigna el aula indicada
 * a todas las occurrences de {@code recurringEventId} desde {@code fromDate} en adelante
 * (saltea las ya pasadas).
 */
@NamedInterface("api")
public record AllocateFromDateRequestDto(
        @NotNull Long recurringEventId,
        @NotNull LocalDate fromDate,
        @NotNull Integer classroomId,
        String observation
) {}
