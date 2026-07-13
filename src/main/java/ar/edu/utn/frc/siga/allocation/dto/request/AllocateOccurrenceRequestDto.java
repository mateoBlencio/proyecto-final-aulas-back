package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.constraints.NotNull;

/** Pedido de asignación manual de una única occurrence al aula indicada. */
public record AllocateOccurrenceRequestDto(
        @NotNull Integer classroomId,
        String observation
) {}
