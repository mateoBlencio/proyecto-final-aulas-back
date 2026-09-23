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

        boolean ownsBuilding = userService.hasGlobalRole(actorEmail, SystemRole.AUXILIAR_AULICO)
                || (item.getDerivedBuildingId() != null
                        && userService.findBuildingIdsForRole(actorEmail, SystemRole.AUXILIAR_AULICO)
                                .contains(item.getDerivedBuildingId()));
        if (!auxiliarAllows(item, action, ownsBuilding)) {
            throw new RoomRequestForbiddenException(action.name());
        }
    }

    public void authorizeRead(RoomRequestItem item, String actorEmail) {
        Optional<Set<Long>> scope = readScope(actorEmail);
        if (scope.isPresent()
                && (item.getDerivedBuildingId() == null || !scope.get().contains(item.getDerivedBuildingId()))) {
            throw new RoomRequestForbiddenException("READ");
        }
    }

    public Optional<Set<Long>> readScope(String actorEmail) {
        if (!userService.hasRole(actorEmail, SystemRole.AUXILIAR_AULICO)
                || userService.hasGlobalRole(actorEmail, SystemRole.AUXILIAR_AULICO)) {
            return Optional.empty();
        }
        return Optional.of(userService.findBuildingIdsForRole(actorEmail, SystemRole.AUXILIAR_AULICO));
    }

    private boolean subsecretariaAllows(RoomRequestItem item, Action action) {
        return switch (item.getStatus()) {
            case NEW -> action == Action.ASSIGN || action == Action.DERIVE || action == Action.CANCEL;
            case DERIVED_TO_BUILDING -> action == Action.RETURN || action == Action.CANCEL;
            case IN_EVALUATION -> action == Action.CANCEL
                    || (item.getDerivedBuildingId() == null && (action == Action.ASSIGN || action == Action.NOTIFY));
            case RESOLVED, CANCELLED -> false;
        };
    }

    private boolean auxiliarAllows(RoomRequestItem item, Action action, boolean ownsBuilding) {
        return switch (item.getStatus()) {
            // Un NEW es de subsecretaría: el auxiliar recién entra en juego cuando ella lo deriva.
            case NEW -> false;
            case DERIVED_TO_BUILDING -> ownsBuilding
                    && (action == Action.ASSIGN || action == Action.RETURN || action == Action.CANCEL);
            case IN_EVALUATION -> ownsBuilding
                    && (action == Action.ASSIGN || action == Action.NOTIFY || action == Action.CANCEL);
            case RESOLVED, CANCELLED -> false;
        };
    }
}
