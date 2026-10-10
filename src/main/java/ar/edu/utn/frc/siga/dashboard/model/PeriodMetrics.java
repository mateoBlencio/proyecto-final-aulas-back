package ar.edu.utn.frc.siga.dashboard.model;

import java.util.List;
import java.util.Map;

/**
 * Result of computing a date range for a scope. {@code units} are the allocations on available days;
 * {@code unallocated} is null when the scope is restricted.
 */
public record PeriodMetrics(
        Map<Long, ScopeMetrics> byBuilding, ScopeMetrics campus, List<BuildingRoomsRow> buildings,
        List<AllocationUnitRow> units, Long unallocated, Availability availability,
        DashboardParameters parameters) {
}
