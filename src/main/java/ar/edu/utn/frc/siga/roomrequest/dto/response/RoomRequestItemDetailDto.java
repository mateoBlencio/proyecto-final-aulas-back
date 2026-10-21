package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RoomRequestItemDetailDto(
        RoomRequestItemDetailHeaderDto request,
        RoomRequestItemResponseDto item,
        @Schema(description = "Acciones que el usuario autenticado puede ejecutar sobre el ítem según rol, estado y edificio derivado. Pista de UI: el back valida igual en cada endpoint.")
        List<RoomRequestAccessControl.Action> allowedActions
) {}
