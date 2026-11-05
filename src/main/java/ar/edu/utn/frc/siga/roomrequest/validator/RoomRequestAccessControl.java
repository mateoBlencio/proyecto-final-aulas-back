package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.roomrequest.exception.RoomRequestForbiddenException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RoomRequestAccessControl {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    public enum Action { ASSIGN, DERIVE, RETURN, CANCEL, NOTIFY, CONFIRM, REOPEN }

    private final UserService userService;

    /** Roles del usuario relevantes para las acciones sobre ítems; se resuelve una vez y se evalúa por ítem. */
    public record Actor(boolean subsecretaria, boolean globalAuxiliar, Set<Long> auxiliarBuildingIds) {
        boolean ownsBuilding(Long buildingId) {
            return globalAuxiliar || (buildingId != null && auxiliarBuildingIds.contains(buildingId));
        }
    }

    public Actor resolveActor(String actorEmail) {
        if (userService.hasRole(actorEmail, SystemRole.SUBSECRETARIA)) {
            return new Actor(true, false, Set.of());
        }
        if (userService.hasGlobalRole(actorEmail, SystemRole.AUXILIAR_AULICO)) {
            return new Actor(false, true, Set.of());
        }
        return new Actor(false, false, userService.findBuildingIdsForRole(actorEmail, SystemRole.AUXILIAR_AULICO));
    }

    public boolean allows(RoomRequestItem item, Actor actor, Action action) {
        return actor.subsecretaria()
                ? subsecretariaAllows(item, action)
                : auxiliarAllows(item, action, actor.ownsBuilding(item.getDerivedBuildingId()));
    }

    /** Pista de UI: oculta REOPEN en un RESOLVED con fecha pasada. En el cambio regular no hay fecha propia y lo decide {@code reopen}. */
    public List<Action> allowedActions(RoomRequestItem item, Actor actor) {
        boolean pastDate = item.getStatus() == RoomRequestStatus.RESOLVED
                && item.getDate() != null && item.getDate().isBefore(LocalDate.now(ZONE));
        return Arrays.stream(Action.values())
                .filter(action -> allows(item, actor, action))
                .filter(action -> !(pastDate && action == Action.REOPEN))
                .toList();
    }

    public void authorize(RoomRequestItem item, String actorEmail, Action action) {
        if (!allows(item, resolveActor(actorEmail), action)) {
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
            case NOTIFIED -> item.getDerivedBuildingId() == null
                    && (action == Action.CANCEL || action == Action.CONFIRM || action == Action.REOPEN);
            case RESOLVED -> item.getDerivedBuildingId() == null && action == Action.REOPEN;
            case CANCELLED -> false;
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
            case NOTIFIED -> item.getDerivedBuildingId() != null && ownsBuilding
                    && (action == Action.CANCEL || action == Action.CONFIRM || action == Action.REOPEN);
            case RESOLVED -> item.getDerivedBuildingId() != null && ownsBuilding && action == Action.REOPEN;
            case CANCELLED -> false;
        };
    }
}
