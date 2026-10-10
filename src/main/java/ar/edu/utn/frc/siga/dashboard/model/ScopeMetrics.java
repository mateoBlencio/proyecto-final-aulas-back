package ar.edu.utn.frc.siga.dashboard.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Absolute metrics of one building or of the campus. Only absolutes, so buildings can be added up;
 * percentages are derived when reading.
 */
public record ScopeMetrics(
        int roomCount, int availableDays, CategoryCounts categories,
        long occupiedRoomMinutes, long availableRoomMinutes,
        long[] occupiedBySlot, long[] availableBySlot,
        Map<String, CategoryCounts> bySource) {

    public static ScopeMetrics empty(int slots) {
        return empty(slots, 0);
    }

    /** Empty metrics for a calendar with the given available days (the days do not depend on the buildings). */
    public static ScopeMetrics empty(int slots, int availableDays) {
        Map<String, CategoryCounts> bySource = new LinkedHashMap<>();
        DashboardRules.SOURCES.forEach(source -> bySource.put(source, CategoryCounts.ZERO));
        return new ScopeMetrics(0, availableDays, CategoryCounts.ZERO, 0, 0, new long[slots], new long[slots], bySource);
    }

    public ScopeMetrics plus(ScopeMetrics other) {
        long[] occupied = new long[occupiedBySlot.length];
        long[] available = new long[availableBySlot.length];
        for (int i = 0; i < occupied.length; i++) {
            occupied[i] = occupiedBySlot[i] + other.occupiedBySlot[i];
            available[i] = availableBySlot[i] + other.availableBySlot[i];
        }
        Map<String, CategoryCounts> merged = new LinkedHashMap<>(bySource);
        other.bySource.forEach((source, counts) -> merged.merge(source, counts, CategoryCounts::plus));
        return new ScopeMetrics(roomCount + other.roomCount, Math.max(availableDays, other.availableDays),
                categories.plus(other.categories), occupiedRoomMinutes + other.occupiedRoomMinutes,
                availableRoomMinutes + other.availableRoomMinutes, occupied, available, merged);
    }
}
