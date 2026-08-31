package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.space.model.PermissionMode;
import java.util.List;

public record ClassroomListItemDto(
        Long id,
        Integer roomNumber,
        Long buildingId,
        String buildingName,
        Long classroomTypeId,
        String classroomTypeDescription,
        Integer capacity,
        List<ClassroomResourceDto> resources,
        String observations,
        boolean enabled,
        PermissionMode permissionMode,
        String allowedDisplay,
        List<ClassroomPermissionTargetDto> permissionTargets
) {
}
