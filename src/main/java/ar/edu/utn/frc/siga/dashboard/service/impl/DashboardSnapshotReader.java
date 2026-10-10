package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.dashboard.dto.response.AllocationTrendResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardParametersResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardReportResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.MonthlyAllocationResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.ReportBuildingResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.ReportCampusResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestCountsResponseDto;
import ar.edu.utn.frc.siga.dashboard.model.CategoryCounts;
import ar.edu.utn.frc.siga.dashboard.model.DashboardParameters;
import ar.edu.utn.frc.siga.dashboard.model.DashboardSnapshot;
import ar.edu.utn.frc.siga.dashboard.model.PeriodMetrics;
import ar.edu.utn.frc.siga.dashboard.model.ScopeMetrics;
import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload;
import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload.RoomRequestCounts;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardSnapshotRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Reads closed-month snapshots and turns them into the trend series and the monthly report. */
@Component
@RequiredArgsConstructor
public class DashboardSnapshotReader {

    private final DashboardSnapshotRepository snapshotRepository;
    private final DashboardMetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** A snapshot row and its parsed content. */
    private record Row(DashboardSnapshot snapshot, SnapshotPayload payload) {
    }

    /** Metrics and room request counts of a scope, summed from one or more rows. */
    private record Aggregate(ScopeMetrics metrics, RoomRequestCounts requests) {
    }

    @Transactional(readOnly = true)
    public AllocationTrendResponseDto trend(BuildingScope scope) {
        Map<YearMonth, List<Row>> byMonth = new LinkedHashMap<>();
        for (DashboardSnapshot snapshot : snapshotRepository.findByCurrentTrueOrderByYearAscMonthAsc()) {
            byMonth.computeIfAbsent(YearMonth.of(snapshot.getYear(), snapshot.getMonth()), k -> new ArrayList<>())
                    .add(new Row(snapshot, parse(snapshot)));
        }

        List<MonthlyAllocationResponseDto> series = new ArrayList<>();
        for (Map.Entry<YearMonth, List<Row>> entry : byMonth.entrySet()) {
            Row campus = campusRow(entry.getValue());
            if (campus == null) {
                continue;
            }
            Aggregate aggregate = aggregate(entry.getValue(), scope);
            series.add(monthly(entry.getKey(), false, campus.payload().from(), campus.payload().to(),
                    aggregate.metrics(), scope.isUnrestricted() ? campus.payload().unallocatedClasses() : null));
        }

        LocalDate today = LocalDate.now(clock);
        PeriodMetrics live = metricsService.compute(today.withDayOfMonth(1), today, scope);
        MonthlyAllocationResponseDto currentMonth = monthly(YearMonth.from(today), true, today.withDayOfMonth(1),
                today, live.campus(), live.unallocated());

        List<YearMonth> closedMonths = series.stream().map(MonthlyAllocationResponseDto::month).toList();
        return new AllocationTrendResponseDto(!scope.isUnrestricted(), closedMonths,
                closedMonths.isEmpty() ? null : closedMonths.getFirst(),
                closedMonths.isEmpty() ? null : closedMonths.getLast(), series, currentMonth);
    }

    @Transactional(readOnly = true)
    public DashboardReportResponseDto report(YearMonth month, BuildingScope scope) {
        List<Row> rows = snapshotRepository.findByYearAndMonthAndCurrentTrue(month.getYear(), month.getMonthValue())
                .stream().map(snapshot -> new Row(snapshot, parse(snapshot))).toList();
        Row campusRow = campusRow(rows);
        if (campusRow == null) {
            throw new ResourceNotFoundException("No hay informe del tablero para el mes " + month);
        }
        SnapshotPayload campus = campusRow.payload();
        DashboardParametersResponseDto parameters = parametersOf(campus);

        List<Row> buildingRows = rows.stream()
                .filter(row -> row.snapshot().getBuildingId() != null)
                .filter(row -> scope.allows(row.snapshot().getBuildingId()))
                .sorted(Comparator.comparing(row -> row.payload().buildingCode(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<ReportBuildingResponseDto> buildings = buildingRows.stream().map(this::reportBuilding).toList();

        ReportCampusResponseDto campusDto;
        if (scope.isUnrestricted()) {
            ScopeMetrics metrics = toMetrics(campus);
            campusDto = reportCampus(metrics, campus.unallocatedClasses(), campus.roomRequests(),
                    campus.unassignedBuildingRoomRequests(), campus);
        } else {
            Aggregate sum = sumBuildings(buildingRows.stream().map(Row::payload).toList(), campus);
            campusDto = reportCampus(sum.metrics(), null, sum.requests(), null, campus);
        }
        return new DashboardReportResponseDto(month, campus.from(), campus.to(), campusRow.snapshot().getGeneratedAt(),
                scope.isUnrestricted() ? campusRow.snapshot().getGeneratedBy() : null,
                scope.isUnrestricted() ? campusRow.snapshot().getRegenerationReason() : null,
                campusRow.snapshot().getRegenerationReason() != null,
                campusRow.snapshot().getSchemaVersion(), !scope.isUnrestricted(), parameters, campusDto, buildings);
    }

    private ReportBuildingResponseDto reportBuilding(Row row) {
        SnapshotPayload p = row.payload();
        ScopeMetrics m = toMetrics(p);
        return new ReportBuildingResponseDto(p.buildingId(), p.buildingCode(), p.buildingName(), p.roomCount(),
                p.availableDays(), DashboardDtoMapper.allocationStatus(m.categories()),
                DashboardDtoMapper.occupancy(m),
                DashboardDtoMapper.hourlyProfile(m.occupiedBySlot(), m.availableBySlot(), firstHour(p)),
                DashboardDtoMapper.bySource(m), requests(p.roomRequests()));
    }

    private ReportCampusResponseDto reportCampus(ScopeMetrics m, Long unallocated, RoomRequestCounts requests,
                                                 RoomRequestCounts unassigned, SnapshotPayload reference) {
        return new ReportCampusResponseDto(m.roomCount(), m.availableDays(),
                DashboardDtoMapper.allocationStatus(m.categories()), unallocated, DashboardDtoMapper.occupancy(m),
                DashboardDtoMapper.hourlyProfile(m.occupiedBySlot(), m.availableBySlot(), firstHour(reference)),
                DashboardDtoMapper.bySource(m), requests(requests),
                unassigned == null ? null : requests(unassigned));
    }

    private static RoomRequestCountsResponseDto requests(RoomRequestCounts c) {
        return new RoomRequestCountsResponseDto(c.resolved(), c.pending(), c.expired());
    }

    private static MonthlyAllocationResponseDto monthly(YearMonth month, boolean partial, LocalDate from, LocalDate to,
                                                        ScopeMetrics metrics, Long unallocated) {
        return new MonthlyAllocationResponseDto(month, partial, from, to,
                DashboardDtoMapper.allocationStatus(metrics.categories()), unallocated,
                DashboardDtoMapper.occupancy(metrics), DashboardDtoMapper.bySource(metrics));
    }

    private static Row campusRow(List<Row> rows) {
        return rows.stream().filter(row -> row.snapshot().getBuildingId() == null).findFirst().orElse(null);
    }

    /** Unrestricted: the stored campus row. Restricted: the sum of its building rows. */
    private static Aggregate aggregate(List<Row> rows, BuildingScope scope) {
        if (scope.isUnrestricted()) {
            SnapshotPayload campus = campusRow(rows).payload();
            return new Aggregate(toMetrics(campus), campus.roomRequests());
        }
        List<SnapshotPayload> buildings = rows.stream()
                .filter(row -> row.snapshot().getBuildingId() != null && scope.allows(row.snapshot().getBuildingId()))
                .map(Row::payload).toList();
        return sumBuildings(buildings, campusRow(rows).payload());
    }

    private static Aggregate sumBuildings(List<SnapshotPayload> buildings, SnapshotPayload reference) {
        ScopeMetrics total = ScopeMetrics.empty(reference.hourlyProfile().size(), reference.availableDays());
        long resolved = 0;
        long expired = 0;
        long pending = 0;
        for (SnapshotPayload p : buildings) {
            total = total.plus(toMetrics(p));
            resolved += p.roomRequests().resolved();
            expired += p.roomRequests().expired();
            pending += p.roomRequests().pending();
        }
        return new Aggregate(total, new RoomRequestCounts(resolved, expired, pending));
    }

    private static ScopeMetrics toMetrics(SnapshotPayload p) {
        long[] occupied = p.hourlyProfile().stream().mapToLong(SnapshotPayload.SlotMinutes::occupiedRoomMinutes).toArray();
        long[] available = p.hourlyProfile().stream().mapToLong(SnapshotPayload.SlotMinutes::availableRoomMinutes).toArray();
        Map<String, CategoryCounts> bySource = new LinkedHashMap<>();
        p.bySource().forEach(s -> bySource.put(s.source(), s.categories()));
        return new ScopeMetrics(p.roomCount(), p.availableDays(), p.categories(), p.occupiedRoomMinutes(),
                p.availableRoomMinutes(), occupied, available, bySource);
    }

    private static int firstHour(SnapshotPayload p) {
        return p.parameters().hoursStart().getHour();
    }

    private static DashboardParametersResponseDto parametersOf(SnapshotPayload p) {
        SnapshotPayload.SnapshotParameters s = p.parameters();
        return DashboardDtoMapper.parameters(new DashboardParameters(s.underusedThresholdPercent(),
                s.maxOverlapMinutes(), s.hoursStart(), s.hoursEnd(), p.rulesVersion()));
    }

    private SnapshotPayload parse(DashboardSnapshot snapshot) {
        return objectMapper.readValue(snapshot.getPayload(), SnapshotPayload.class);
    }
}
