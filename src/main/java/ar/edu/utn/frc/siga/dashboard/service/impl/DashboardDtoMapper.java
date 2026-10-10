package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.dashboard.dto.response.AllocationStatusResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardParametersResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.HourlyOccupancyResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.OccupancyResponseDto;
import ar.edu.utn.frc.siga.dashboard.model.CategoryCounts;
import ar.edu.utn.frc.siga.dashboard.model.DashboardParameters;
import ar.edu.utn.frc.siga.dashboard.model.DashboardRules;
import ar.edu.utn.frc.siga.dashboard.model.ScopeMetrics;
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
}
