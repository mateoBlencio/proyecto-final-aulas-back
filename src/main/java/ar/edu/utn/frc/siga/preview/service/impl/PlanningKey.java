package ar.edu.utn.frc.siga.preview.service.impl;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record PlanningKey(Long eventId, int roomSlot) implements Comparable<PlanningKey> {

    private static final Comparator<PlanningKey> ORDER =
            Comparator.comparing(PlanningKey::eventId).thenComparingInt(PlanningKey::roomSlot);

    public static PlanningKey parse(String planningId) {
        int separator = planningId.indexOf(':');
        if (separator < 0) {
            return new PlanningKey(Long.valueOf(planningId), 1);
        }
        return new PlanningKey(Long.valueOf(planningId.substring(0, separator)),
                Integer.parseInt(planningId.substring(separator + 1)));
    }

    // Los espejos de un recurrente pueden ser parciales: el divisor es el mínimo de aulas simultáneas
    // sobre las fechas de la clave, para no subdimensionar nunca.
    public static Map<PlanningKey, Integer> divisors(Map<PlanningKey, List<LocalDate>> datesByKey) {
        Map<List<Object>, Integer> slotsPerEventDate = new HashMap<>();
        datesByKey.forEach((key, dates) -> dates.forEach(
                date -> slotsPerEventDate.merge(List.of(key.eventId(), date), 1, Integer::sum)));
        Map<PlanningKey, Integer> divisors = new HashMap<>();
        datesByKey.forEach((key, dates) -> divisors.put(key, dates.stream()
                .mapToInt(date -> slotsPerEventDate.get(List.of(key.eventId(), date)))
                .min().orElse(1)));
        return divisors;
    }

    public static Set<Long> mirroredEventIds(Collection<PlanningKey> keys) {
        return keys.stream().filter(key -> key.roomSlot() > 1).map(PlanningKey::eventId)
                .collect(Collectors.toSet());
    }

    public String toPlanningId() {
        return roomSlot == 1 ? String.valueOf(eventId) : eventId + ":" + roomSlot;
    }

    @Override
    public int compareTo(PlanningKey other) {
        return ORDER.compare(this, other);
    }
}
