package ar.edu.utn.frc.siga.realtime.internal;

import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.common.realtime.LiveTopic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Junta los cambios por tema hasta el próximo reparto. Un cambio sin edificio absorbe a los demás del mismo tema. */
class LiveChangeBuffer {

    private final Map<LiveTopic, Pending> pendingByTopic = new EnumMap<>(LiveTopic.class);

    synchronized void add(LiveChange change) {
        Pending pending = pendingByTopic.computeIfAbsent(change.topic(), topic -> new Pending());
        pending.unscoped |= change.unscoped();
        pending.buildingIds.addAll(change.buildingIds());
        pending.classroomIds.addAll(change.classroomIds());
    }

    synchronized List<Drained> drain() {
        List<Drained> drained = new ArrayList<>();
        pendingByTopic.forEach((topic, pending) ->
                drained.add(new Drained(topic, pending.buildingIds, pending.classroomIds, pending.unscoped)));
        pendingByTopic.clear();
        return drained;
    }

    record Drained(LiveTopic topic, Set<Long> buildingIds, Set<Long> classroomIds, boolean unscoped) {

        Drained {
            buildingIds = Set.copyOf(buildingIds);
            classroomIds = Set.copyOf(classroomIds);
        }
    }

    private static final class Pending {
        private boolean unscoped;
        private final Set<Long> buildingIds = new HashSet<>();
        private final Set<Long> classroomIds = new HashSet<>();
    }
}
