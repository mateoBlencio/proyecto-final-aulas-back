package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;

public record ClassroomPermissionTargetDto(
        PermissionTargetKind targetKind,
        Long targetId,
        String name
) {
}
