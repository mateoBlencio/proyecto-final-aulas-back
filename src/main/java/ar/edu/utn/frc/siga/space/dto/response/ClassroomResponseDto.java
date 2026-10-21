package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ClassroomResponseDto(
        Long id,
        Integer roomNumber,
        Integer capacity,
        Long buildingId,
        String buildingName,
        Long classroomTypeId,
        String classroomTypeDescription,
        UnavailableReason unavailableReason,
        boolean currentInSysacad,
        @Schema(description = "Pista de UI: el usuario puede editar el aula (false si está desactivada). El back valida igual en cada endpoint.")
        boolean canUpdate,
        @Schema(description = "Pista de UI: el usuario puede activar/desactivar el aula. El back valida igual en cada endpoint.")
        boolean canActivate,
        @Schema(description = "Pista de UI: el usuario puede asignar eventos al aula (false si el aula no está disponible: unavailableReason distinto de null). El back valida igual en cada endpoint.")
        boolean canAllocate
) {

    /** Constructor sin flags (en false) para quien arma el DTO a mano. */
    public ClassroomResponseDto(Long id, Integer roomNumber, Integer capacity, Long buildingId, String buildingName,
                                Long classroomTypeId, String classroomTypeDescription,
                                UnavailableReason unavailableReason, boolean currentInSysacad) {
        this(id, roomNumber, capacity, buildingId, buildingName, classroomTypeId, classroomTypeDescription,
                unavailableReason, currentInSysacad, false, false, false);
    }
}
