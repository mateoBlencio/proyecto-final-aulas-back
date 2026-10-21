package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record BuildingResponseDto(
        Long id,
        String name,
        Boolean active,
        UnavailableReason unavailableReason,
        @Schema(description = "Pista de UI: el usuario puede activar/desactivar el edificio. El back valida igual en cada endpoint.")
        boolean canActivate,
        @Schema(description = "Pista de UI: el usuario puede crear aulas en el edificio (false si está desactivado). El back valida igual en cada endpoint.")
        boolean canCreateClassroom
) {

    /** Constructor sin flags (en false) para quien arma el DTO a mano. */
    public BuildingResponseDto(Long id, String name, Boolean active, UnavailableReason unavailableReason) {
        this(id, name, active, unavailableReason, false, false);
    }
}
