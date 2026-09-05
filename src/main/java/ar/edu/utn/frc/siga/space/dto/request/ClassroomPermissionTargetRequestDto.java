package ar.edu.utn.frc.siga.space.dto.request;

import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import jakarta.validation.constraints.NotNull;

public record ClassroomPermissionTargetRequestDto(
        @NotNull PermissionTargetKind targetKind,
        @NotNull Long targetId
) {
}
