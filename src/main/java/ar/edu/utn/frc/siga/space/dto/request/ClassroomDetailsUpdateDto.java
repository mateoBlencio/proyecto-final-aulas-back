package ar.edu.utn.frc.siga.space.dto.request;

import ar.edu.utn.frc.siga.space.model.PermissionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClassroomDetailsUpdateDto(
        @NotNull Long classroomTypeId,
        @Size(max = 500) String observations,
        @NotNull PermissionMode permissionMode,
        @Valid List<ClassroomPermissionTargetRequestDto> permissionTargets,
        @Valid List<ClassroomResourceRequestDto> resources
) {
}
