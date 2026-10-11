package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.dashboard.dto.response.AllocationStatusResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardParametersResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.ExcludedDayResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.HourlyOccupancyResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.OccupancyResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.ReportBuildingResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.ReportCampusResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestCountsResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.SourceBreakdownResponseDto;
import ar.edu.utn.frc.siga.dashboard.model.Availability;
import ar.edu.utn.frc.siga.dashboard.model.CategoryCounts;
import ar.edu.utn.frc.siga.dashboard.model.DashboardParameters;
import ar.edu.utn.frc.siga.dashboard.model.DashboardRules;
import ar.edu.utn.frc.siga.dashboard.model.ScopeMetrics;
import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload.RoomRequestCounts;
import java.util.ArrayList;
import java.util.List;

/** Turns absolute metrics into response DTOs: percentages and hours are derived here, never stored. */
final class DashboardDtoMapper {

    private DashboardDtoMapper() {
    }

    static DashboardParametersResponseDto parameters(DashboardParameters p) {
        return new DashboardParametersResponseDto(p.underusedThresholdPercent(), p.maxOverlapMinutes(),
                p.hoursStart(), p.hoursEnd(), p.rulesVersion());
    }

    static AllocationStatusResponseDto allocationStatus(CategoryCounts c) {
        long total = c.total();
        return new AllocationStatusResponseDto(c.overlap(), c.overcrowded(), c.underused(), c.adequate(), total,
                DashboardRules.percent(c.overlap(), total), DashboardRules.percent(c.overcrowded(), total),
                DashboardRules.percent(c.underused(), total), DashboardRules.percent(c.adequate(), total));
    }

    static OccupancyResponseDto occupancy(long occupiedMinutes, long availableMinutes) {
        return new OccupancyResponseDto(DashboardRules.hours(occupiedMinutes), DashboardRules.hours(availableMinutes),
                DashboardRules.hours(availableMinutes - occupiedMinutes),
                DashboardRules.percent(occupiedMinutes, availableMinutes));
    }

    static OccupancyResponseDto occupancy(ScopeMetrics m) {
        return occupancy(m.occupiedRoomMinutes(), m.availableRoomMinutes());
    }

    static List<HourlyOccupancyResponseDto> hourlyProfile(long[] occupiedBySlot, long[] availableBySlot,
                                                          int firstHour) {
        List<HourlyOccupancyResponseDto> profile = new ArrayList<>();
        for (int i = 0; i < occupiedBySlot.length; i++) {
            profile.add(new HourlyOccupancyResponseDto(firstHour + i, occupiedBySlot[i], availableBySlot[i],
                    DashboardRules.percent(occupiedBySlot[i], availableBySlot[i])));
        }
        return profile;
    }

    static List<HourlyOccupancyResponseDto> hourlyProfile(ScopeMetrics m, DashboardParameters p) {
        return hourlyProfile(m.occupiedBySlot(), m.availableBySlot(), p.firstSlotHour());
    }

    static List<SourceBreakdownResponseDto> bySource(ScopeMetrics m) {
        return m.bySource().entrySet().stream()
                .map(entry -> new SourceBreakdownResponseDto(entry.getKey(), allocationStatus(entry.getValue())))
                .toList();
    }

    static RoomRequestCountsResponseDto roomRequests(RoomRequestCounts c) {
        return new RoomRequestCountsResponseDto(c.resolved(), c.pending(), c.expired());
    }

    static ReportBuildingResponseDto reportBuilding(Long buildingId, Integer code, String name, ScopeMetrics m,
                                                    RoomRequestCounts requests, int firstHour) {
        return new ReportBuildingResponseDto(buildingId, code, name, m.roomCount(), m.availableDays(),
                allocationStatus(m.categories()), occupancy(m),
                hourlyProfile(m.occupiedBySlot(), m.availableBySlot(), firstHour), bySource(m),
                roomRequests(requests));
    }

    /** {@code unassigned} null: the field is left out of the response. */
    static ReportCampusResponseDto reportCampus(ScopeMetrics m, Long unallocated, RoomRequestCounts requests,
                                                RoomRequestCounts unassigned, int firstHour) {
        return new ReportCampusResponseDto(m.roomCount(), m.availableDays(), allocationStatus(m.categories()),
                unallocated, occupancy(m), hourlyProfile(m.occupiedBySlot(), m.availableBySlot(), firstHour),
                bySource(m), roomRequests(requests), unassigned == null ? null : roomRequests(unassigned));
    }

    static List<ExcludedDayResponseDto> excludedDays(Availability availability) {
        return availability.excludedDays().stream()
                .map(d -> new ExcludedDayResponseDto(d.date(), d.reason(), d.description()))
                .toList();
    }
}
