package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.RoomDate;
import ar.edu.utn.frc.siga.common.util.TimeRanges;
import ar.edu.utn.frc.siga.dashboard.model.AllocationCategory;
import ar.edu.utn.frc.siga.dashboard.model.AllocationUnitRow;
import ar.edu.utn.frc.siga.dashboard.model.BuildingRoomsRow;
import ar.edu.utn.frc.siga.dashboard.model.CalculationInput;
import ar.edu.utn.frc.siga.dashboard.model.CategoryCounts;
import ar.edu.utn.frc.siga.dashboard.model.ClassifiedAllocation;
import ar.edu.utn.frc.siga.dashboard.model.DashboardParameters;
import ar.edu.utn.frc.siga.dashboard.model.ScopeMetrics;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure dashboard calculation: classification, occupancy and hourly profile. No Spring, no I/O. */
public final class DashboardCalculator {

    private DashboardCalculator() {
    }

    /**
     * Category and weight of each allocation id. The unit is the group (same event and date): one
     * overlapping room makes the whole group OVERLAP, otherwise it is compared against the summed
     * capacity. Units on unavailable days must be filtered out beforehand.
     */
    public static Map<Long, ClassifiedAllocation> classify(List<AllocationUnitRow> units, DashboardParameters params) {
        Set<Long> overlapping = new HashSet<>();
        Clashes.within(units,
                u -> List.of(new RoomDate(u.classroomId(), u.date())),
                (a, b) -> !a.eventId().equals(b.eventId())
                        && TimeRanges.overlapMinutes(a.startTime(), a.endTime(), b.startTime(), b.endTime())
                        > params.maxOverlapMinutes(),
                (a, b, key) -> {
                    overlapping.add(a.allocationId());
                    overlapping.add(b.allocationId());
                    return key;
                });

        Map<EventDate, List<AllocationUnitRow>> groups = new LinkedHashMap<>();
        for (AllocationUnitRow unit : units) {
            groups.computeIfAbsent(new EventDate(unit.eventId(), unit.date()), k -> new ArrayList<>()).add(unit);
        }

        Map<Long, ClassifiedAllocation> result = new HashMap<>();
        for (List<AllocationUnitRow> group : groups.values()) {
            AllocationCategory category = group.stream().anyMatch(u -> overlapping.contains(u.allocationId()))
                    ? AllocationCategory.OVERLAP
                    : classifyByCapacity(group, params.underusedThresholdPercent());
            ClassifiedAllocation classified = new ClassifiedAllocation(category, 1.0 / group.size());
            group.forEach(u -> result.put(u.allocationId(), classified));
        }
        return result;
    }

    private record EventDate(Long eventId, LocalDate date) {
    }

    private static AllocationCategory classifyByCapacity(List<AllocationUnitRow> group, int thresholdPercent) {
        int enrolled = group.getFirst().enrolled();
        Integer capacity = 0;
        for (AllocationUnitRow unit : group) {
            if (unit.capacity() == null) {
                capacity = null;
                break;
            }
            capacity += unit.capacity();
        }
        if (capacity != null && enrolled > capacity) {
            return AllocationCategory.OVERCROWDED;
        }
        if (capacity != null && (long) enrolled * 100 < (long) thresholdPercent * capacity) {
            return AllocationCategory.UNDERUSED;
        }
        return AllocationCategory.ADEQUATE;
    }

    /** Metrics per building (every building of the input appears, even without allocations). */
    public static Map<Long, ScopeMetrics> calculate(CalculationInput input) {
        DashboardParameters params = input.params();
        int slotCount = params.lastSlotHour() - params.firstSlotHour() + 1;
        int bandMinutes = params.endMinute() - params.startMinute();
        int days = input.availableDays().size();

        List<AllocationUnitRow> units = input.units().stream()
                .filter(u -> input.availableDays().contains(u.date()))
                .toList();
        Map<Long, ClassifiedAllocation> classified = classify(units, params);

        Map<Long, ScopeMetrics> result = new LinkedHashMap<>();
        for (BuildingRoomsRow building : input.buildings()) {
            result.put(building.buildingId(), emptyBuilding(building, days, bandMinutes, slotCount, params));
        }

        Map<Long, CategoryCounts> counts = new HashMap<>();
        Map<Long, Map<String, CategoryCounts>> bySource = new HashMap<>();
        for (AllocationUnitRow unit : units) {
            if (!result.containsKey(unit.buildingId())) {
                continue;
            }
            ClassifiedAllocation c = classified.get(unit.allocationId());
            CategoryCounts share = CategoryCounts.ZERO.with(c.category(), c.weight());
            counts.merge(unit.buildingId(), share, CategoryCounts::plus);
            bySource.computeIfAbsent(unit.buildingId(), k -> new LinkedHashMap<>())
                    .merge(unit.source(), share, CategoryCounts::plus);
        }

        Map<Long, long[]> occupiedBySlot = new HashMap<>();
        Map<Long, Long> occupiedMinutes = new HashMap<>();
        Map<RoomDate, List<AllocationUnitRow>> byRoomDate = new LinkedHashMap<>();
        for (AllocationUnitRow unit : units) {
            if (result.containsKey(unit.buildingId())) {
                byRoomDate.computeIfAbsent(new RoomDate(unit.classroomId(), unit.date()), k -> new ArrayList<>()).add(unit);
            }
        }
        for (List<AllocationUnitRow> group : byRoomDate.values()) {
            Long buildingId = group.getFirst().buildingId();
            long[] slots = occupiedBySlot.computeIfAbsent(buildingId, k -> new long[slotCount]);
            for (int[] interval : mergedClippedIntervals(group, params)) {
                occupiedMinutes.merge(buildingId, (long) (interval[1] - interval[0]), Long::sum);
                for (int i = 0; i < slotCount; i++) {
                    int slotStart = (params.firstSlotHour() + i) * 60;
                    slots[i] += overlap(interval[0], interval[1], slotStart, slotStart + 60);
                }
            }
        }

        result.replaceAll((buildingId, base) -> {
            Map<String, CategoryCounts> sources = new LinkedHashMap<>(base.bySource());
            bySource.getOrDefault(buildingId, Map.of()).forEach(sources::put);
            return new ScopeMetrics(base.roomCount(), base.availableDays(),
                    counts.getOrDefault(buildingId, CategoryCounts.ZERO),
                    occupiedMinutes.getOrDefault(buildingId, 0L), base.availableRoomMinutes(),
                    occupiedBySlot.getOrDefault(buildingId, new long[slotCount]), base.availableBySlot(), sources);
        });
        return result;
    }

    /** Campus metrics: the sum of the given buildings. */
    public static ScopeMetrics sum(Collection<ScopeMetrics> metrics, DashboardParameters params) {
        ScopeMetrics total = ScopeMetrics.empty(params.lastSlotHour() - params.firstSlotHour() + 1);
        for (ScopeMetrics m : metrics) {
            total = total.plus(m);
        }
        return total;
    }

    private static ScopeMetrics emptyBuilding(BuildingRoomsRow building, int days, int bandMinutes,
                                              int slotCount, DashboardParameters params) {
        ScopeMetrics base = ScopeMetrics.empty(slotCount);
        long[] available = new long[slotCount];
        for (int i = 0; i < slotCount; i++) {
            int slotStart = (params.firstSlotHour() + i) * 60;
            available[i] = (long) building.roomCount() * days
                    * overlap(slotStart, slotStart + 60, params.startMinute(), params.endMinute());
        }
        return new ScopeMetrics(building.roomCount(), days, base.categories(), 0,
                (long) building.roomCount() * days * bandMinutes, base.occupiedBySlot(), available, base.bySource());
    }

    /** Intervals of one room and date, clipped to the daily band, sorted and merged. */
    private static List<int[]> mergedClippedIntervals(List<AllocationUnitRow> group, DashboardParameters params) {
        List<int[]> clipped = new ArrayList<>();
        for (AllocationUnitRow unit : group) {
            int start = Math.max(unit.startMinute(), params.startMinute());
            int end = Math.min(unit.endMinute(), params.endMinute());
            if (start < end) {
                clipped.add(new int[]{start, end});
            }
        }
        clipped.sort(Comparator.comparingInt(i -> i[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] interval : clipped) {
            int[] last = merged.isEmpty() ? null : merged.getLast();
            if (last != null && interval[0] <= last[1]) {
                last[1] = Math.max(last[1], interval[1]);
            } else {
                merged.add(interval);
            }
        }
        return merged;
    }

    private static int overlap(int start1, int end1, int start2, int end2) {
        return Math.max(0, Math.min(end1, end2) - Math.max(start1, start2));
    }
}
