package ar.edu.utn.frc.siga.dashboard.service.impl;

import static java.time.DayOfWeek.MONDAY;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardBadgesResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardWeekResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.ExcludedDayResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.OpenRoomRequestsResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomsInUseResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.WeekBuildingResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.WeekCampusResponseDto;
import ar.edu.utn.frc.siga.dashboard.model.AllocationUnitRow;
import ar.edu.utn.frc.siga.dashboard.model.BuildingRoomsRow;
import ar.edu.utn.frc.siga.dashboard.model.CategoryCounts;
import ar.edu.utn.frc.siga.dashboard.model.PeriodMetrics;
import ar.edu.utn.frc.siga.dashboard.model.PendingItemRow;
import ar.edu.utn.frc.siga.dashboard.model.ScopeMetrics;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardReadRepository;
import ar.edu.utn.frc.siga.dashboard.service.DashboardQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardQueryServiceImpl implements DashboardQueryService {

    private static final String NEW = "NEW";
    private static final String DERIVED_TO_BUILDING = "DERIVED_TO_BUILDING";
    private static final String IN_EVALUATION = "IN_EVALUATION";
    private static final int EXPIRING_SOON_HOURS = 48;
    private static final int BADGE_WINDOW_DAYS = 7;

    private final DashboardMetricsService metricsService;
    private final DashboardReadRepository repository;
    private final BuildingScopeResolver buildingScopeResolver;
    private final Clock clock;

    @Override
    public DashboardWeekResponseDto week(LocalDate weekOf) {
        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.DASHBOARD_READ);
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = (weekOf == null ? today : weekOf).with(previousOrSame(MONDAY));
        boolean currentWeek = monday.equals(today.with(previousOrSame(MONDAY)));

        PeriodMetrics period = metricsService.compute(monday, monday.plusDays(5), scope);
        Map<Long, RoomsInUseResponseDto> inUse = currentWeek ? roomsInUse(period, today) : Map.of();
        List<PendingItemRow> openItems = repository.findOpenItems();
        Map<Long, OpenRoomRequestsResponseDto> open = openRoomRequests(period.buildings(), openItems);

        List<WeekBuildingResponseDto> buildings = period.buildings().stream()
                .map(b -> weekBuilding(b, period, inUse.get(b.buildingId()), open.get(b.buildingId())))
                .toList();
        WeekCampusResponseDto campus = new WeekCampusResponseDto(
                DashboardDtoMapper.occupancy(period.campus()),
                currentWeek ? campusInUse(inUse.values()) : null,
                DashboardDtoMapper.allocationStatus(period.campus().categories()),
                DashboardDtoMapper.hourlyProfile(period.campus(), period.parameters()),
                period.unallocated(),
                campusOpenRoomRequests(scope, openItems, open));

        List<ExcludedDayResponseDto> excluded = period.availability().excludedDays().stream()
                .map(d -> new ExcludedDayResponseDto(d.date(), d.reason(), d.description()))
                .toList();
        return new DashboardWeekResponseDto(monday, monday.plusDays(5), currentWeek, !scope.isUnrestricted(),
                DashboardDtoMapper.parameters(period.parameters()), excluded, campus, buildings);
    }

    @Override
    public DashboardBadgesResponseDto badges() {
        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.DASHBOARD_READ);
        LocalDate today = LocalDate.now(clock);
        List<PendingItemRow> openItems = repository.findOpenItems();
        PeriodMetrics window = metricsService.compute(today, today.plusDays(BADGE_WINDOW_DAYS - 1), scope);

        long awaiting = openItems.stream().filter(item -> awaitsAction(item, scope)).count();
        LocalDateTime now = LocalDateTime.now(clock);
        long expiring = openItems.stream().filter(item -> inScope(item, scope))
                .filter(item -> expiresWithin(item, now)).count();
        CategoryCounts categories = window.campus().categories();
        return new DashboardBadgesResponseDto(awaiting, expiring, window.unallocated(),
                categories.overlap() + categories.overcrowded());
    }

    /**
     * Who acts on each state: without building restriction, NEW and IN_EVALUATION not derived; with
     * restriction, what was derived to its buildings (DERIVED_TO_BUILDING, or IN_EVALUATION that the building
     * allocates and notifies).
     */
    private static boolean awaitsAction(PendingItemRow item, BuildingScope scope) {
        if (scope.isUnrestricted()) {
            return NEW.equals(item.status())
                    || (IN_EVALUATION.equals(item.status()) && item.derivedBuildingId() == null);
        }
        return (DERIVED_TO_BUILDING.equals(item.status()) || IN_EVALUATION.equals(item.status()))
                && inScope(item, scope);
    }

    private static boolean inScope(PendingItemRow item, BuildingScope scope) {
        return scope.isUnrestricted() || (item.derivedBuildingId() != null && scope.allows(item.derivedBuildingId()));
    }

    /** Dated items only; one without a start time counts until the end of its day. */
    private static boolean expiresWithin(PendingItemRow item, LocalDateTime now) {
        if (item.date() == null) {
            return false;
        }
        LocalDateTime start = item.date().atTime(item.startTime() == null ? LocalTime.MAX : item.startTime());
        return !start.isBefore(now) && !start.isAfter(now.plusHours(EXPIRING_SOON_HOURS));
    }

    private WeekBuildingResponseDto weekBuilding(BuildingRoomsRow building, PeriodMetrics period,
                                                 RoomsInUseResponseDto inUse, OpenRoomRequestsResponseDto open) {
        ScopeMetrics m = period.byBuilding().get(building.buildingId());
        return new WeekBuildingResponseDto(building.buildingId(), building.code(), building.name(),
                DashboardDtoMapper.occupancy(m), inUse,
                DashboardDtoMapper.allocationStatus(m.categories()),
                DashboardDtoMapper.hourlyProfile(m, period.parameters()), open);
    }

    /** Distinct rooms with a class in progress right now, per building. Empty when today is not an available day. */
    private Map<Long, RoomsInUseResponseDto> roomsInUse(PeriodMetrics period, LocalDate today) {
        LocalTime now = LocalTime.now(clock);
        Map<Long, Set<Long>> roomsByBuilding = new HashMap<>();
        for (AllocationUnitRow unit : period.units()) {
            if (unit.date().equals(today) && !unit.startTime().isAfter(now) && now.isBefore(unit.endTime())) {
                roomsByBuilding.computeIfAbsent(unit.buildingId(), k -> new HashSet<>()).add(unit.classroomId());
            }
        }
        Map<Long, RoomsInUseResponseDto> result = new HashMap<>();
        for (BuildingRoomsRow building : period.buildings()) {
            int inUse = roomsByBuilding.getOrDefault(building.buildingId(), Set.of()).size();
            result.put(building.buildingId(), new RoomsInUseResponseDto(inUse, building.roomCount()));
        }
        return result;
    }

    private static RoomsInUseResponseDto campusInUse(Iterable<RoomsInUseResponseDto> buildings) {
        int inUse = 0;
        int total = 0;
        for (RoomsInUseResponseDto b : buildings) {
            inUse += b.inUse();
            total += b.total();
        }
        return new RoomsInUseResponseDto(inUse, total);
    }

    /** Per building: items derived to it (DERIVED_TO_BUILDING) and the age of the oldest since derivation. */
    private Map<Long, OpenRoomRequestsResponseDto> openRoomRequests(List<BuildingRoomsRow> buildings,
                                                                    List<PendingItemRow> openItems) {
        List<PendingItemRow> derived = openItems.stream()
                .filter(item -> DERIVED_TO_BUILDING.equals(item.status()))
                .toList();
        Map<Long, OpenRoomRequestsResponseDto> result = new HashMap<>();
        for (BuildingRoomsRow building : buildings) {
            List<PendingItemRow> items = derived.stream()
                    .filter(item -> building.buildingId().equals(item.derivedBuildingId()))
                    .toList();
            result.put(building.buildingId(), new OpenRoomRequestsResponseDto(items.size(),
                    oldest(items.stream().map(PendingItemRow::derivedAt))));
        }
        return result;
    }

    /** Unrestricted campus: every open item, aged from the creation of the request. Restricted: the sum of its buildings. */
    private OpenRoomRequestsResponseDto campusOpenRoomRequests(BuildingScope scope, List<PendingItemRow> openItems,
                                                              Map<Long, OpenRoomRequestsResponseDto> byBuilding) {
        if (scope.isUnrestricted()) {
            return new OpenRoomRequestsResponseDto(openItems.size(),
                    oldest(openItems.stream().map(PendingItemRow::createdAt)));
        }
        long count = byBuilding.values().stream().mapToLong(OpenRoomRequestsResponseDto::count).sum();
        Instant oldest = byBuilding.values().stream().map(OpenRoomRequestsResponseDto::oldestSince)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return new OpenRoomRequestsResponseDto(count, oldest);
    }

    private Instant oldest(Stream<LocalDateTime> timestamps) {
        return timestamps.filter(Objects::nonNull).min(Comparator.naturalOrder())
                .map(t -> t.atZone(clock.getZone()).toInstant()).orElse(null);
    }
}
