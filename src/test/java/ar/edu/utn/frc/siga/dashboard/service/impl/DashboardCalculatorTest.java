package ar.edu.utn.frc.siga.dashboard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ar.edu.utn.frc.siga.dashboard.model.AllocationCategory;
import ar.edu.utn.frc.siga.dashboard.model.AllocationUnitRow;
import ar.edu.utn.frc.siga.dashboard.model.BuildingRoomsRow;
import ar.edu.utn.frc.siga.dashboard.model.CalculationInput;
import ar.edu.utn.frc.siga.dashboard.model.ClassifiedAllocation;
import ar.edu.utn.frc.siga.dashboard.model.DashboardParameters;
import ar.edu.utn.frc.siga.dashboard.model.ScopeMetrics;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DashboardCalculatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final DashboardParameters PARAMS =
            new DashboardParameters(50, 40, LocalTime.of(8, 0), LocalTime.of(23, 0), 1);

    private static AllocationUnitRow unit(long id, long room, long building, LocalDate date, long event,
                                          int startHour, int startMin, int durationMin, int enrolled, Integer capacity) {
        int start = startHour * 60 + startMin;
        return new AllocationUnitRow(id, room, building, "SYSACAD", date, event, start, start + durationMin,
                enrolled, capacity);
    }

    private static CalculationInput input(Set<LocalDate> days, List<BuildingRoomsRow> buildings,
                                          AllocationUnitRow... units) {
        return new CalculationInput(days, buildings, List.of(units), PARAMS);
    }

    private static Map<Long, AllocationCategory> categories(List<AllocationUnitRow> units) {
        return DashboardCalculator.classify(units, PARAMS).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().category()));
    }

    private static final List<BuildingRoomsRow> ONE_BUILDING = List.of(new BuildingRoomsRow(1L, 2, "Central", 3));

    @Test
    void overlapAboveToleranceMarksBothAllocations() {
        var a = unit(1, 10, 1, MONDAY, 100, 10, 0, 60, 20, 30);
        var b = unit(2, 10, 1, MONDAY, 200, 10, 19, 60, 20, 30); // 41 min overlap

        Map<Long, AllocationCategory> result = categories(List.of(a, b));

        assertThat(result).containsEntry(1L, AllocationCategory.OVERLAP).containsEntry(2L, AllocationCategory.OVERLAP);
    }

    @Test
    void overlapEqualToToleranceIsNotOverlap() {
        var a = unit(1, 10, 1, MONDAY, 100, 10, 0, 60, 20, 30);
        var b = unit(2, 10, 1, MONDAY, 200, 10, 20, 60, 20, 30); // 40 min overlap

        Map<Long, AllocationCategory> result = categories(List.of(a, b));

        assertThat(result.values()).containsOnly(AllocationCategory.ADEQUATE);
    }

    @Test
    void overlapBetweenOccurrencesOfSameEventIsNotOverlap() {
        var a = unit(1, 10, 1, MONDAY, 100, 10, 0, 120, 40, 30);
        var b = unit(2, 10, 1, MONDAY, 100, 11, 0, 120, 40, 30); // 60 min overlap, same event

        Map<Long, AllocationCategory> result = categories(List.of(a, b));

        assertThat(result.values()).containsOnly(AllocationCategory.ADEQUATE);
    }

    @Test
    void overlapTakesPrecedenceOverOvercrowding() {
        var a = unit(1, 10, 1, MONDAY, 100, 10, 0, 120, 99, 30);
        var b = unit(2, 10, 1, MONDAY, 200, 11, 0, 120, 10, 30);

        Map<Long, AllocationCategory> result = categories(List.of(a, b));

        assertThat(result).containsEntry(1L, AllocationCategory.OVERLAP);
    }

    @Test
    void sameTimeInDifferentRoomsOrDatesIsNotOverlap() {
        var a = unit(1, 10, 1, MONDAY, 100, 10, 0, 60, 20, 30);
        var b = unit(2, 11, 1, MONDAY, 200, 10, 0, 60, 20, 30);
        var c = unit(3, 10, 1, TUESDAY, 300, 10, 0, 60, 20, 30);

        Map<Long, AllocationCategory> result = categories(List.of(a, b, c));

        assertThat(result.values()).containsOnly(AllocationCategory.ADEQUATE);
    }

    @Test
    void capacityBoundaries() {
        var over = unit(1, 10, 1, MONDAY, 1, 8, 0, 30, 31, 30);
        var exact = unit(2, 11, 1, MONDAY, 2, 8, 0, 30, 30, 30);
        var under = unit(3, 12, 1, MONDAY, 3, 8, 0, 30, 14, 30);
        var half = unit(4, 13, 1, MONDAY, 4, 8, 0, 30, 15, 30);

        Map<Long, AllocationCategory> result = categories(List.of(over, exact, under, half));

        assertThat(result)
                .containsEntry(1L, AllocationCategory.OVERCROWDED)
                .containsEntry(2L, AllocationCategory.ADEQUATE)
                .containsEntry(3L, AllocationCategory.UNDERUSED)
                .containsEntry(4L, AllocationCategory.ADEQUATE);
    }

    @Test
    void zeroCapacityHasNoSpecialRule() {
        var empty = unit(1, 10, 1, MONDAY, 1, 8, 0, 30, 0, 0);
        var one = unit(2, 11, 1, MONDAY, 2, 8, 0, 30, 1, 0);

        Map<Long, AllocationCategory> result = categories(List.of(empty, one));

        assertThat(result)
                .containsEntry(1L, AllocationCategory.ADEQUATE)
                .containsEntry(2L, AllocationCategory.OVERCROWDED);
    }

    @Test
    void nullCapacityIsAdequate() {
        var u = unit(1, 10, 1, MONDAY, 1, 8, 0, 30, 500, null);

        assertThat(categories(List.of(u))).containsEntry(1L, AllocationCategory.ADEQUATE);
    }

    @Test
    void classEndingAfterBandIsClipped() {
        var late = unit(1, 10, 1, MONDAY, 1, 22, 0, 65, 20, 30); // 22:00-23:05

        ScopeMetrics metrics = DashboardCalculator.calculate(input(Set.of(MONDAY), ONE_BUILDING, late)).get(1L);

        assertThat(metrics.occupiedRoomMinutes()).isEqualTo(60);
    }

    @Test
    void classStartingBeforeBandIsClipped() {
        var early = unit(1, 10, 1, MONDAY, 1, 7, 30, 90, 20, 30); // 07:30-09:00

        ScopeMetrics metrics = DashboardCalculator.calculate(input(Set.of(MONDAY), ONE_BUILDING, early)).get(1L);

        assertThat(metrics.occupiedRoomMinutes()).isEqualTo(60);
    }

    @Test
    void classOutsideBandContributesNothing() {
        var night = unit(1, 10, 1, MONDAY, 1, 23, 0, 30, 20, 30);

        ScopeMetrics metrics = DashboardCalculator.calculate(input(Set.of(MONDAY), ONE_BUILDING, night)).get(1L);

        assertThat(metrics.occupiedRoomMinutes()).isZero();
        assertThat(metrics.categories().total()).isEqualTo(1);
    }

    @Test
    void overlappingClassesInSameRoomAreCountedOnce() {
        var a = unit(1, 10, 1, MONDAY, 100, 10, 0, 120, 20, 30); // 10-12
        var b = unit(2, 10, 1, MONDAY, 200, 11, 0, 120, 20, 30); // 11-13

        ScopeMetrics metrics = DashboardCalculator.calculate(input(Set.of(MONDAY), ONE_BUILDING, a, b)).get(1L);

        assertThat(metrics.occupiedRoomMinutes()).isEqualTo(180);
    }

    @Test
    void hourlyProfileSplitsClassAcrossSlots() {
        var u = unit(1, 10, 1, MONDAY, 1, 10, 30, 60, 20, 30); // 10:30-11:30

        ScopeMetrics metrics = DashboardCalculator.calculate(input(Set.of(MONDAY), ONE_BUILDING, u)).get(1L);

        assertThat(metrics.occupiedBySlot()).hasSize(15);
        assertThat(metrics.occupiedBySlot()[10 - 8]).isEqualTo(30);
        assertThat(metrics.occupiedBySlot()[11 - 8]).isEqualTo(30);
        assertThat(metrics.occupiedBySlot()[0]).isZero();
        assertThat(PARAMS.firstSlotHour()).isEqualTo(8);
        assertThat(PARAMS.lastSlotHour()).isEqualTo(22);
    }

    @Test
    void allocationOnUnavailableDateIsIgnored() {
        var u = unit(1, 10, 1, TUESDAY, 1, 10, 0, 60, 99, 30);

        ScopeMetrics metrics = DashboardCalculator.calculate(input(Set.of(MONDAY), ONE_BUILDING, u)).get(1L);

        assertThat(metrics.categories().total()).isZero();
        assertThat(metrics.occupiedRoomMinutes()).isZero();
    }

    @Test
    void availableMinutesAreRoomsTimesDaysTimesBand() {
        Set<LocalDate> days = Set.of(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3), MONDAY.plusDays(4));

        ScopeMetrics metrics = DashboardCalculator.calculate(input(days, ONE_BUILDING)).get(1L);

        assertThat(metrics.availableRoomMinutes()).isEqualTo(3L * 5 * 900);
        assertThat(metrics.availableBySlot()).hasSize(15).containsOnly(3L * 5 * 60);
        assertThat(metrics.availableDays()).isEqualTo(5);
    }

    @Test
    void campusIsTheSumOfBuildings() {
        var buildings = List.of(new BuildingRoomsRow(1L, 2, "Central", 3), new BuildingRoomsRow(2L, 4, "Malvinas", 2));
        var a = unit(1, 10, 1, MONDAY, 1, 10, 0, 60, 31, 30);
        var b = unit(2, 20, 2, MONDAY, 2, 10, 30, 60, 20, 30);

        Map<Long, ScopeMetrics> byBuilding = DashboardCalculator.calculate(input(Set.of(MONDAY), buildings, a, b));
        ScopeMetrics campus = DashboardCalculator.sum(byBuilding.values(), PARAMS);

        assertThat(campus.roomCount()).isEqualTo(5);
        assertThat(campus.categories().overcrowded()).isEqualTo(1);
        assertThat(campus.categories().adequate()).isEqualTo(1);
        assertThat(campus.occupiedRoomMinutes()).isEqualTo(120);
        assertThat(campus.availableRoomMinutes()).isEqualTo(5L * 900);
        assertThat(campus.occupiedBySlot()[10 - 8]).isEqualTo(60 + 30);
        assertThat(campus.occupiedBySlot()[11 - 8]).isEqualTo(30);
        assertThat(campus.availableBySlot()[0]).isEqualTo(5L * 60);
    }

    @Test
    void breakdownBySourceKeepsFixedOrderAndCounts() {
        var manual = new AllocationUnitRow(1L, 10L, 1L, "MANUAL", MONDAY, 1L, 600, 660, 31, 30);
        var sysacad = unit(2, 11, 1, MONDAY, 2, 10, 0, 60, 20, 30);

        ScopeMetrics metrics = DashboardCalculator.calculate(input(Set.of(MONDAY), ONE_BUILDING, manual, sysacad)).get(1L);

        assertThat(metrics.bySource().keySet()).containsExactly("MANUAL", "AUTOMATIC", "IMPORTED", "SYSACAD");
        assertThat(metrics.bySource().get("MANUAL").overcrowded()).isEqualTo(1);
        assertThat(metrics.bySource().get("SYSACAD").adequate()).isEqualTo(1);
        assertThat(metrics.bySource().get("AUTOMATIC").total()).isZero();
    }

    @Test
    void buildingWithoutAllocationsStillAppears() {
        Map<Long, ScopeMetrics> result = DashboardCalculator.calculate(input(Set.of(MONDAY), ONE_BUILDING));

        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L).categories().total()).isZero();
    }

    @Test
    void singleRoomAllocationHasFullWeight() {
        var u = unit(1, 10, 1, MONDAY, 1, 8, 0, 30, 20, 30);

        ClassifiedAllocation result = DashboardCalculator.classify(List.of(u), PARAMS).get(1L);

        assertThat(result.category()).isEqualTo(AllocationCategory.ADEQUATE);
        assertThat(result.weight()).isEqualTo(1.0);
    }

    @Test
    void groupOvercrowdedAgainstSummedCapacityCountsOnceInTotal() {
        var a = unit(1, 10, 1, MONDAY, 100, 8, 0, 60, 129, 70);
        var b = unit(2, 11, 1, MONDAY, 100, 8, 0, 60, 129, 25);

        Map<Long, ClassifiedAllocation> result = DashboardCalculator.classify(List.of(a, b), PARAMS);
        ScopeMetrics metrics = DashboardCalculator.calculate(input(Set.of(MONDAY), ONE_BUILDING, a, b)).get(1L);

        assertThat(result.values()).allSatisfy(c -> {
            assertThat(c.category()).isEqualTo(AllocationCategory.OVERCROWDED);
            assertThat(c.weight()).isEqualTo(0.5);
        });
        assertThat(metrics.categories().overcrowded()).isCloseTo(1.0, within(1e-9));
        assertThat(metrics.categories().total()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void groupWithinSummedCapacityIsAdequate() {
        var a = unit(1, 10, 1, MONDAY, 100, 8, 0, 60, 80, 70);
        var b = unit(2, 11, 1, MONDAY, 100, 8, 0, 60, 80, 30);

        assertThat(categories(List.of(a, b))).containsEntry(1L, AllocationCategory.ADEQUATE)
                .containsEntry(2L, AllocationCategory.ADEQUATE);
    }

    @Test
    void groupEnrolledEqualToSummedCapacityIsAdequate() {
        var a = unit(1, 10, 1, MONDAY, 100, 8, 0, 60, 100, 70);
        var b = unit(2, 11, 1, MONDAY, 100, 8, 0, 60, 100, 30);

        assertThat(categories(List.of(a, b)).values()).containsOnly(AllocationCategory.ADEQUATE);
    }

    @Test
    void groupBelowThresholdOfSummedCapacityIsUnderused() {
        var a = unit(1, 10, 1, MONDAY, 100, 8, 0, 60, 49, 70);
        var b = unit(2, 11, 1, MONDAY, 100, 8, 0, 60, 49, 30);

        assertThat(categories(List.of(a, b)).values()).containsOnly(AllocationCategory.UNDERUSED);
    }

    @Test
    void groupWithUnknownCapacityIsAdequate() {
        var a = unit(1, 10, 1, MONDAY, 100, 8, 0, 60, 500, 70);
        var b = unit(2, 11, 1, MONDAY, 100, 8, 0, 60, 500, null);

        assertThat(categories(List.of(a, b)).values()).containsOnly(AllocationCategory.ADEQUATE);
    }

    @Test
    void sameEventOnDifferentDatesAreSeparateGroups() {
        var a = unit(1, 10, 1, MONDAY, 100, 8, 0, 60, 40, 70);
        var b = unit(2, 10, 1, TUESDAY, 100, 8, 0, 60, 40, 70);
        var c = unit(3, 11, 1, TUESDAY, 100, 8, 0, 60, 40, 70);

        Map<Long, ClassifiedAllocation> result = DashboardCalculator.classify(List.of(a, b, c), PARAMS);

        assertThat(result.get(1L).weight()).isEqualTo(1.0);
        assertThat(result.get(2L).weight()).isEqualTo(0.5);
        assertThat(result.get(3L).weight()).isEqualTo(0.5);
    }

    @Test
    void groupInTwoBuildingsSplitsHalfPerBuildingAndAddsUpToOneOnCampus() {
        var buildings = List.of(new BuildingRoomsRow(1L, 2, "Central", 3), new BuildingRoomsRow(2L, 4, "Malvinas", 2));
        var a = unit(1, 10, 1, MONDAY, 100, 8, 0, 60, 129, 70);
        var b = unit(2, 20, 2, MONDAY, 100, 8, 0, 60, 129, 25);

        Map<Long, ScopeMetrics> byBuilding = DashboardCalculator.calculate(input(Set.of(MONDAY), buildings, a, b));
        ScopeMetrics campus = DashboardCalculator.sum(byBuilding.values(), PARAMS);

        assertThat(byBuilding.get(1L).categories().overcrowded()).isCloseTo(0.5, within(1e-9));
        assertThat(byBuilding.get(2L).categories().overcrowded()).isCloseTo(0.5, within(1e-9));
        assertThat(campus.categories().overcrowded()).isCloseTo(1.0, within(1e-9));
        assertThat(campus.categories().total()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void oneOverlappingUnitMakesWholeGroupOverlap() {
        var a = unit(1, 10, 1, MONDAY, 100, 10, 0, 60, 20, 70);
        var b = unit(2, 11, 1, MONDAY, 100, 10, 0, 60, 20, 30);
        var other = unit(3, 11, 1, MONDAY, 200, 10, 0, 60, 10, 30);

        Map<Long, ClassifiedAllocation> result = DashboardCalculator.classify(List.of(a, b, other), PARAMS);

        assertThat(result.get(1L).category()).isEqualTo(AllocationCategory.OVERLAP);
        assertThat(result.get(2L).category()).isEqualTo(AllocationCategory.OVERLAP);
        assertThat(result.get(3L).category()).isEqualTo(AllocationCategory.OVERLAP);
        assertThat(result.get(1L).weight()).isEqualTo(0.5);
    }
}
