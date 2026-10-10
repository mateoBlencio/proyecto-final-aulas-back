package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestBuildingCountsResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestCountsResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestStatsResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestYearResponseDto;
import ar.edu.utn.frc.siga.dashboard.model.BuildingRoomsRow;
import ar.edu.utn.frc.siga.dashboard.model.PendingItemRow;
import ar.edu.utn.frc.siga.dashboard.model.RequestCountRow;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardReadRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Room request counts per year and building: resolved, expired by the system, and pending right now. */
@Component
@RequiredArgsConstructor
public class DashboardRoomRequestStats {

    private final DashboardReadRepository repository;
    private final Clock clock;

    public RoomRequestStatsResponseDto stats(int years, BuildingScope scope) {
        int currentYear = Year.now(clock).getValue();
        int firstYear = currentYear - years + 1;
        LocalDateTime from = LocalDateTime.of(firstYear, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(currentYear + 1, 1, 1, 0, 0);

        List<BuildingRoomsRow> buildings = repository.findBuildings(scope);
        List<RequestCountRow> resolved = repository.countResolved(from, to);
        List<RequestCountRow> expired = repository.countExpired(from, to);
        List<PendingItemRow> open = repository.findOpenItems();

        List<RoomRequestYearResponseDto> result = new ArrayList<>();
        for (int year = firstYear; year <= currentYear; year++) {
            boolean partial = year == currentYear;
            RoomRequestYearResponseDto row = yearRow(year, partial, buildings, scope, resolved, expired, open);
            if (hasAnyCount(row)) {
                result.add(row);
            }
        }
        return new RoomRequestStatsResponseDto(!scope.isUnrestricted(), result);
    }

    private RoomRequestYearResponseDto yearRow(int year, boolean partial, List<BuildingRoomsRow> buildings,
                                               BuildingScope scope, List<RequestCountRow> resolved,
                                               List<RequestCountRow> expired, List<PendingItemRow> open) {
        Map<Long, Long> resolvedByBuilding = byBuilding(resolved, year);
        Map<Long, Long> expiredByBuilding = byBuilding(expired, year);
        Map<Long, Long> pendingByBuilding = new HashMap<>();
        long pendingAll = 0;
        long pendingUnassigned = 0;
        for (PendingItemRow item : open) {
            pendingAll++;
            if (item.derivedBuildingId() == null) {
                pendingUnassigned++;
            } else {
                pendingByBuilding.merge(item.derivedBuildingId(), 1L, Long::sum);
            }
        }

        List<RoomRequestBuildingCountsResponseDto> rows = new ArrayList<>();
        long resolvedInScope = 0;
        long expiredInScope = 0;
        long pendingInScope = 0;
        for (BuildingRoomsRow building : buildings) {
            long r = resolvedByBuilding.getOrDefault(building.buildingId(), 0L);
            long e = expiredByBuilding.getOrDefault(building.buildingId(), 0L);
            long p = pendingByBuilding.getOrDefault(building.buildingId(), 0L);
            resolvedInScope += r;
            expiredInScope += e;
            pendingInScope += p;
            rows.add(new RoomRequestBuildingCountsResponseDto(building.buildingId(), building.code(), building.name(),
                    r, partial ? p : null, e));
        }

        if (!scope.isUnrestricted()) {
            return new RoomRequestYearResponseDto(year, partial,
                    new RoomRequestCountsResponseDto(resolvedInScope, partial ? pendingInScope : null, expiredInScope),
                    rows, null);
        }
        long resolvedCampus = resolved.stream().filter(row -> row.year() == year && row.campus())
                .mapToLong(RequestCountRow::total).sum();
        long expiredCampus = expired.stream().filter(row -> row.year() == year).mapToLong(RequestCountRow::total).sum();
        long expiredUnassigned = expiredByBuilding.getOrDefault(null, 0L);
        return new RoomRequestYearResponseDto(year, partial,
                new RoomRequestCountsResponseDto(resolvedCampus, partial ? pendingAll : null, expiredCampus),
                rows,
                new RoomRequestCountsResponseDto(0, partial ? pendingUnassigned : null, expiredUnassigned));
    }

    /** Building rows of one year (null key: no building). The campus row is left out. */
    private static Map<Long, Long> byBuilding(List<RequestCountRow> rows, int year) {
        Map<Long, Long> result = new HashMap<>();
        for (RequestCountRow row : rows) {
            if (row.year() == year && !row.campus()) {
                result.merge(row.buildingId(), row.total(), Long::sum);
            }
        }
        return result;
    }

    private static boolean hasAnyCount(RoomRequestYearResponseDto row) {
        boolean campus = isNonZero(row.campus());
        boolean unassigned = row.unassignedBuilding() != null && isNonZero(row.unassignedBuilding());
        boolean buildings = row.buildings().stream()
                .anyMatch(b -> b.resolved() != 0 || b.expired() != 0 || (b.pending() != null && b.pending() != 0));
        return campus || unassigned || buildings;
    }

    private static boolean isNonZero(RoomRequestCountsResponseDto counts) {
        return counts.resolved() != 0 || counts.expired() != 0 || (counts.pending() != null && counts.pending() != 0);
    }
}
