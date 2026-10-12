package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.dashboard.model.AllocationUnitRow;
import ar.edu.utn.frc.siga.dashboard.model.Availability;
import ar.edu.utn.frc.siga.dashboard.model.BuildingRoomsRow;
import ar.edu.utn.frc.siga.dashboard.model.CalculationInput;
import ar.edu.utn.frc.siga.dashboard.model.DashboardParameters;
import ar.edu.utn.frc.siga.dashboard.model.PeriodMetrics;
import ar.edu.utn.frc.siga.dashboard.model.ScopeMetrics;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardReadRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Computes the allocation metrics of a date range for a building scope. Shared by week, month and snapshots. */
@Component
@RequiredArgsConstructor
public class DashboardMetricsService {

    private final DashboardReadRepository repository;
    private final AvailabilityCalendar availabilityCalendar;
    private final DashboardParametersProvider parametersProvider;

    @Transactional(readOnly = true)
    public PeriodMetrics compute(LocalDate from, LocalDate to, BuildingScope scope) {
        DashboardParameters parameters = parametersProvider.current();
        Availability availability = availabilityCalendar.resolve(from, to);
        List<BuildingRoomsRow> buildings = repository.findBuildings(scope);
        List<AllocationUnitRow> units = repository.findUnits(from, to, scope).stream()
                .filter(unit -> availability.availableDays().contains(unit.date()))
                .toList();

        Map<Long, ScopeMetrics> byBuilding = DashboardCalculator.calculate(
                new CalculationInput(availability.availableDays(), buildings, units, parameters));
        ScopeMetrics campus = DashboardCalculator.sum(byBuilding.values(), parameters);

        Long unallocated = scope.isUnrestricted() ? countUnallocated(from, to, availability) : null;
        return new PeriodMetrics(byBuilding, campus, buildings, units, unallocated, availability, parameters);
    }

    private long countUnallocated(LocalDate from, LocalDate to, Availability availability) {
        return repository.countUnallocatedByDate(from, to).entrySet().stream()
                .filter(entry -> availability.availableDays().contains(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }
}
