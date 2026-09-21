package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.roomrequest.exception.RoomRequestForbiddenException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Quién puede hacer qué sobre un pedido de aula: refleja la matriz rol × estado que hoy sólo
 * aplica el front ({@code getAllowedActions}), más el recorte por edificio del auxiliar áulico.
 */
@Component
@RequiredArgsConstructor
public class RoomRequestAccessControl {

    public enum Action { ASSIGN, DERIVE, RETURN, CANCEL, NOTIFY }

    private final UserService userService;

    public void authorize(RoomRequestItem item, String actorEmail, Action action) {
        if (userService.hasRole(actorEmail, SystemRole.SUBSECRETARIA)) {
            if (!subsecretariaAllows(item, action)) {
                throw new RoomRequestForbiddenException(action.name());
            }
            return;
        }

        Set<Long> buildingIds = userService.findBuildingIdsForRole(actorEmail, SystemRole.AUXILIAR_AULICO);
        boolean ownsBuilding = item.getDerivedBuildingId() != null
                && buildingIds.contains(item.getDerivedBuildingId());
        if (!auxiliarAllows(item, action, ownsBuilding)) {
            throw new RoomRequestForbiddenException(action.name());
        }
    }

    /** Edificios a los que hay que acotar una consulta para este actor; vacío = sin recorte (subsecretaría). */
    public Optional<Set<Long>> readScope(String actorEmail) {
        if (userService.hasRole(actorEmail, SystemRole.SUBSECRETARIA)) {
            return Optional.empty();
        }
        return Optional.of(userService.findBuildingIdsForRole(actorEmail, SystemRole.AUXILIAR_AULICO));
    }

    private boolean subsecretariaAllows(RoomRequestItem item, Action action) {
        return switch (item.getStatus()) {
            case NEW -> action == Action.ASSIGN || action == Action.DERIVE || action == Action.CANCEL;
            case DERIVED_TO_BUILDING -> action == Action.RETURN || action == Action.CANCEL;
            // Una vez que un auxiliar toma el pedido (pasó por un edificio), subsecretaría deja de operarlo.
            case IN_EVALUATION -> item.getDerivedBuildingId() == null
                    && (action == Action.ASSIGN || action == Action.NOTIFY);
            case RESOLVED, CANCELLED -> false;
        };
    }

    private boolean auxiliarAllows(RoomRequestItem item, Action action, boolean ownsBuilding) {
        return switch (item.getStatus()) {
            // Un NEW es de subsecretaría: el auxiliar recién entra en juego cuando ella lo deriva.
            case NEW -> false;
            case DERIVED_TO_BUILDING -> ownsBuilding
                    && (action == Action.ASSIGN || action == Action.RETURN || action == Action.CANCEL);
            case IN_EVALUATION -> ownsBuilding && (action == Action.ASSIGN || action == Action.NOTIFY);
            case RESOLVED, CANCELLED -> false;
        };
    }
}
