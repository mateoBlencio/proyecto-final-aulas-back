package ar.edu.utn.frc.siga.realtime.internal;

import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.common.security.BuildingScope;

import java.util.Set;

/** Aviso ya agrupado y con los edificios resueltos, listo para repartir. */
record LiveDelivery(LiveTopic topic, Set<Long> buildingIds, boolean unscoped) {

    LiveDelivery {
        buildingIds = Set.copyOf(buildingIds);
    }

    /** Un cambio sin edificio lo recibe todo el que vea el tema; uno con edificios, quien vea alguno de ellos. */
    boolean reaches(BuildingScope scope) {
        if (scope == null || isDenied(scope)) return false;
        if (unscoped || scope.isUnrestricted()) return true;
        return buildingIds.stream().anyMatch(scope::allows);
    }

    private static boolean isDenied(BuildingScope scope) {
        return !scope.isUnrestricted() && scope.buildingIds().isEmpty();
    }
}
