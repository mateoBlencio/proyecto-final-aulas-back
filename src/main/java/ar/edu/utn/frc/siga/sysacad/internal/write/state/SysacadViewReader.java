package ar.edu.utn.frc.siga.sysacad.internal.write.state;

import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadEmptyViewException;
import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;
import ar.edu.utn.frc.siga.sysacad.internal.model.NotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.sync.ReconcileOutcome;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.DryRunChange;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadViewReconciler;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadViewReader {

    private final ScheduleViewFetcher fetcher;
    private final SysacadCatalogMapper mapper;
    private final SysacadScheduleRepository scheduleRepository;
    private final SysacadViewReconciler reconciler;
    private final SysacadProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public record ViewReadResult(List<Long> directChangeScheduleIds, List<DryRunChange> dryRunChanges) {
    }

    private record RowKey(Integer specialty, Integer plan, Integer subject, Integer commission, Integer dayOfWeek,
            Integer semester) {

        static RowKey of(SysacadAllocationDto row) {
            return new RowKey(row.specialtyCode(), row.studyPlanCode(), row.subjectCode(), row.commissionNumber(),
                    row.dayOfWeek().getValue(), row.semester());
        }

        static RowKey of(SysacadSchedule schedule) {
            return new RowKey(schedule.getSpecialty(), schedule.getPlan(), schedule.getSubject(),
                    schedule.getCommission(), schedule.getDayOfWeek(), schedule.getSemester());
        }
    }

    private record Room(Integer building, Integer room) {
    }

    /** Un error de red o una vista vacía se propaga: no se toca ningún horario. */
    public ViewReadResult read() {
        List<RawSchedule> raw = fetcher.fetch();
        if (raw.isEmpty()) {
            throw new SysacadEmptyViewException(SysacadView.ASIGNACIONES.name());
        }
        Map<RowKey, List<SysacadAllocationDto>> rowsByKey = raw.stream()
                .map(mapper::toAllocation)
                .filter(Objects::nonNull)
                .filter(row -> row.commissionNumber() != null)
                .collect(Collectors.groupingBy(RowKey::of, LinkedHashMap::new, Collectors.toList()));
        return new TransactionTemplate(transactionManager).execute(status -> reconcileAll(rowsByKey));
    }

    private ViewReadResult reconcileAll(Map<RowKey, List<SysacadAllocationDto>> rowsByKey) {
        Instant now = clock.instant();
        boolean dryRun = properties.getWrite().mode() == SysacadWriteMode.DRY_RUN;
        List<Long> directChanges = new ArrayList<>();
        List<DryRunChange> dryRunChanges = new ArrayList<>();
        for (SysacadSchedule schedule : scheduleRepository.findByPresentInViewTrue()) {
            if (schedule.getNotSendableReason() == NotSendableReason.KEY_CONFLICT) {
                continue;
            }
            List<SysacadAllocationDto> rows = rowsByKey.get(RowKey.of(schedule));
            if (rows == null) {
                continue;
            }
            Set<Room> rooms = rows.stream()
                    .map(row -> new Room(row.buildingCode(), row.roomNumber()))
                    .collect(Collectors.toSet());
            if (rooms.size() != 1) {
                log.warn("Filas de la vista con distinto edificio o aula para el horario {}: se saltea", schedule.getId());
                continue;
            }
            Room room = rooms.iterator().next();
            Integer previousBuilding = schedule.getKnownBuilding();
            Integer previousRoom = schedule.getKnownRoom();
            ReconcileOutcome outcome = reconciler.reconcile(schedule, room.building(), room.room(), now);
            if (outcome == ReconcileOutcome.DIRECT_CHANGE) {
                directChanges.add(schedule.getId());
            } else if (outcome == ReconcileOutcome.EXTERNAL_CHANGE_APPLIED && dryRun) {
                dryRunChanges.add(new DryRunChange(schedule.getId(), previousBuilding, previousRoom,
                        room.building(), room.room()));
            }
        }
        return new ViewReadResult(directChanges, dryRunChanges);
    }
}
