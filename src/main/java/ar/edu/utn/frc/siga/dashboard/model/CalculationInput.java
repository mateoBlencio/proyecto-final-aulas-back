package ar.edu.utn.frc.siga.dashboard.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record CalculationInput(
        Set<LocalDate> availableDays, List<BuildingRoomsRow> buildings,
        List<AllocationUnitRow> units, DashboardParameters params) {
}
