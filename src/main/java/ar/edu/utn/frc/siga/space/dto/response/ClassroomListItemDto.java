package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import io.swagger.v3.oas.annotations.media.Schema;
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
        List<ClassroomPermissionTargetDto> permissionTargets,
        UnavailableReason unavailableReason,
        boolean currentInSysacad,
        @Schema(description = "Pista de UI: el usuario puede editar el aula (false si está desactivada). El back valida igual en cada endpoint.")
        boolean canUpdate,
        @Schema(description = "Pista de UI: el usuario puede activar/desactivar el aula. El back valida igual en cada endpoint.")
        boolean canActivate,
        @Schema(description = "Pista de UI: el usuario puede asignar eventos al aula (false si el aula no está disponible: unavailableReason distinto de null). El back valida igual en cada endpoint.")
        boolean canAllocate
) {
}
