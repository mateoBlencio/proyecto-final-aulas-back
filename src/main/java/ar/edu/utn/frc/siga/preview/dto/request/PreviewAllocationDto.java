package ar.edu.utn.frc.siga.preview.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Asignación propuesta {evento → aula} tal como quedó tras los ajustes del usuario en
 * el front.
 */
public record PreviewAllocationDto(@NotNull Long eventId, Integer classroomId) {
}
