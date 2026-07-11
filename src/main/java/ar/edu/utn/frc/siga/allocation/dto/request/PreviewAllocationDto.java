package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Asignación propuesta {evento → aula} tal como quedó tras los ajustes del usuario en
 * el front. Compartido entre validate-move y confirm.
 */
public record PreviewAllocationDto(@NotNull Long eventId, Integer classroomId) {
}
