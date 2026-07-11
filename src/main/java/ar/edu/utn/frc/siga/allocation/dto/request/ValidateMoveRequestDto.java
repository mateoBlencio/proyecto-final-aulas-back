package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Estado de la propuesta ajustada por el usuario en el momento de soltar un bloque en
 * otra aula: el movimiento en sí ({@code eventId} al aula {@code classroomId}) y la
 * lista completa de asignaciones tal como quedaron en el front, ya que el backend solo
 * cachea la corrida original del solver, no las ediciones posteriores.
 */
public record ValidateMoveRequestDto(
        @NotNull Long eventId,
        @NotNull Integer classroomId,
        @NotNull @Valid List<PreviewAllocationDto> currentAllocations) {
}
