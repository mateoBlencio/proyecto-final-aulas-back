package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardReportResponseDto;
import ar.edu.utn.frc.siga.dashboard.exception.MonthNotClosedException;
import ar.edu.utn.frc.siga.dashboard.model.DashboardSnapshotRegeneration;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardSnapshotRegenerationRepository;
import ar.edu.utn.frc.siga.dashboard.model.AllocationUnitRow;
import ar.edu.utn.frc.siga.dashboard.model.BuildingRoomsRow;
import ar.edu.utn.frc.siga.dashboard.model.CategoryCounts;
import ar.edu.utn.frc.siga.dashboard.model.DashboardParameters;
import ar.edu.utn.frc.siga.dashboard.model.DashboardRules;
import ar.edu.utn.frc.siga.dashboard.model.DashboardSnapshot;
import ar.edu.utn.frc.siga.dashboard.model.PeriodMetrics;
import ar.edu.utn.frc.siga.dashboard.model.WindowRoomRequests;
import ar.edu.utn.frc.siga.dashboard.model.ScopeMetrics;
import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload;
import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload.RoomRequestCounts;
import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload.SlotMinutes;
import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload.SnapshotParameters;
import ar.edu.utn.frc.siga.dashboard.model.SnapshotPayload.SourceCounts;
import ar.edu.utn.frc.siga.dashboard.repository.DashboardSnapshotRepository;
import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardSnapshotServiceImpl implements DashboardSnapshotService {

    static final String SYSTEM_ACTOR = "SISTEMA";

    private final DashboardMetricsService metricsService;
    private final DashboardRoomRequestCounter roomRequestCounter;
    private final DashboardSnapshotRepository snapshotRepository;
    private final DashboardSnapshotRegenerationRepository regenerationRepository;
    private final DashboardSnapshotReader snapshotReader;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Override
    public void generateMissingMonths() {
        YearMonth lastClosed = YearMonth.now(clock).minusMonths(1);
        List<DashboardSnapshot> current = snapshotRepository.findByCurrentTrueOrderByYearAscMonthAsc();

        List<YearMonth> missing = new ArrayList<>();
        if (current.isEmpty()) {
            missing.add(lastClosed);
        } else {
            Set<YearMonth> withCampus = new HashSet<>();
            current.stream().filter(s -> s.getBuildingId() == null)
                    .forEach(s -> withCampus.add(YearMonth.of(s.getYear(), s.getMonth())));
            YearMonth first = YearMonth.of(current.getFirst().getYear(), current.getFirst().getMonth());
            for (YearMonth month = first; !month.isAfter(lastClosed); month = month.plusMonths(1)) {
                if (!withCampus.contains(month)) {
                    missing.add(month);
                }
            }
        }

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        for (YearMonth month : missing) {
            try {
                transaction.executeWithoutResult(status -> generate(month, SYSTEM_ACTOR, null));
                log.info("Snapshot del tablero generado para {}", month);
            } catch (RuntimeException e) {
                log.error("No se pudo generar el snapshot del tablero de {}", month, e);
            }
        }
    }

    @Override
    @Transactional
    @AuditOperation("Regeneración de informe del tablero")
    public DashboardReportResponseDto regenerate(YearMonth month, String reason, String actor) {
        DashboardDateLimits.requireWithinRange(month, clock);
        if (!month.isBefore(YearMonth.now(clock))) {
            throw new MonthNotClosedException(month);
        }
        // Serializes concurrent regenerations of the same month: the second waits and then retires the first's rows.
        snapshotRepository.lockMonth(month.getYear() * 100L + month.getMonthValue());
        if (snapshotRepository.findByYearAndMonthAndCurrentTrue(month.getYear(), month.getMonthValue()).isEmpty()) {
            throw new ResourceNotFoundException("No hay informe del tablero para el mes " + month);
        }
        snapshotRepository.retire(month.getYear(), month.getMonthValue());
        generate(month, actor, reason);
        regenerationRepository.save(new DashboardSnapshotRegeneration(
                month.getYear(), month.getMonthValue(), reason, clock.instant()));
        AuditOperations.describe("Regeneración del informe " + month + " del tablero: " + reason);
        return snapshotReader.report(month, BuildingScope.unrestricted());
    }

    @Override
    @Transactional
    public void generate(YearMonth month, String actor, String reason) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        PeriodMetrics period = metricsService.compute(from, to, BuildingScope.unrestricted());

        WindowRoomRequests requests = roomRequestCounter.count(from, to);

        Instant now = clock.instant();
        for (BuildingRoomsRow building : period.buildings()) {
            save(month, from, to, building, period.byBuilding().get(building.buildingId()), period.parameters(),
                    null, requests.forBuilding(building.buildingId()), null, now, actor, reason);
        }
        save(month, from, to, null, period.campus(), period.parameters(), period.unallocated(), requests.campus(),
                requests.unassignedBuilding(), now, actor, reason);
    }

    private void save(YearMonth month, LocalDate from, LocalDate to, BuildingRoomsRow building,
                      ScopeMetrics metrics, DashboardParameters params, Long unallocated,
                      RoomRequestCounts roomRequests, RoomRequestCounts unassigned,
                      Instant now, String actor, String reason) {
        List<SlotMinutes> profile = new ArrayList<>();
        for (int i = 0; i < metrics.occupiedBySlot().length; i++) {
            profile.add(new SlotMinutes(params.firstSlotHour() + i, metrics.occupiedBySlot()[i],
                    metrics.availableBySlot()[i]));
        }
        List<SourceCounts> bySource = DashboardRules.SOURCES.stream()
                .map(source -> new SourceCounts(source, metrics.bySource().getOrDefault(source, CategoryCounts.ZERO)))
                .toList();
        SnapshotPayload payload = new SnapshotPayload(DashboardRules.SCHEMA_VERSION, params.rulesVersion(), month,
                from, to,
                new SnapshotParameters(params.underusedThresholdPercent(), params.maxOverlapMinutes(),
                        params.hoursStart(), params.hoursEnd()),
                building == null ? null : building.buildingId(), building == null ? null : building.code(),
                building == null ? null : building.name(),
                metrics.roomCount(), metrics.availableDays(), metrics.categories(), unallocated,
                metrics.occupiedRoomMinutes(), metrics.availableRoomMinutes(), profile, bySource,
                roomRequests, unassigned);

        snapshotRepository.save(DashboardSnapshot.builder()
                .year(month.getYear()).month(month.getMonthValue())
                .buildingId(building == null ? null : building.buildingId())
                .schemaVersion(DashboardRules.SCHEMA_VERSION).rulesVersion(params.rulesVersion())
                .generatedAt(now).generatedBy(actor).regenerationReason(reason)
                .payload(objectMapper.writeValueAsString(payload))
                .build());
    }
}
