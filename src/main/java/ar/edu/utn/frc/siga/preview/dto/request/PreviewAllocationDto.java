package ar.edu.utn.frc.siga.preview.dto.request;

import jakarta.validation.constraints.NotNull;

public record PreviewAllocationDto(@NotNull Long eventId, Long classroomId) {
}
