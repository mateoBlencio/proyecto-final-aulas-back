package ar.edu.utn.frc.siga.common.realtime;

import java.util.Set;

/**
 * Un cambio confirmado en la base. Lleva los edificios afectados, o las aulas cuando el edificio todavía no se
 * resolvió. {@code unscoped} marca un cambio sin edificio conocido: lo recibe todo el que vea el tema.
 */
public record LiveChange(LiveTopic topic, Set<Long> buildingIds, Set<Long> classroomIds, boolean unscoped) {

    public LiveChange {
        buildingIds = Set.copyOf(buildingIds);
        classroomIds = Set.copyOf(classroomIds);
    }

    public static LiveChange inBuildings(LiveTopic topic, Set<Long> buildingIds) {
        return new LiveChange(topic, buildingIds, Set.of(), false);
    }

    public static LiveChange inClassrooms(LiveTopic topic, Set<Long> classroomIds) {
        return new LiveChange(topic, Set.of(), classroomIds, false);
    }

    public static LiveChange unscoped(LiveTopic topic) {
        return new LiveChange(topic, Set.of(), Set.of(), true);
    }
}
