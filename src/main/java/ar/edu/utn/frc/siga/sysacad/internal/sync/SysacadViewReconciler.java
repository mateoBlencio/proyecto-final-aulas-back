package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadRuntimeSettings;
import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleLogEntry;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadViewReconciler {

    private final SysacadRuntimeSettings runtimeSettings;
    private final RecentSendLookup recentSendLookup;
    private final SysacadScheduleLogRepository logRepository;

    public ReconcileOutcome reconcile(SysacadSchedule schedule, Integer building, Integer room, Instant now) {
        if (building == null || room == null) {
            return ReconcileOutcome.NO_CHANGE;
        }
        boolean sameAsKnown = Objects.equals(schedule.getKnownBuilding(), building)
                && Objects.equals(schedule.getKnownRoom(), room);

        if (schedule.getStatus() == SysacadScheduleStatus.REVIEW) {
            if (sameAsKnown) {
                clearDifference(schedule);
            } else {
                recordDifference(schedule, building, room, now);
            }
            return ReconcileOutcome.NO_CHANGE;
        }
        if (sameAsKnown) {
            clearDifference(schedule);
            return ReconcileOutcome.NO_CHANGE;
        }
        if (recentSendLookup.isRecentSend(schedule, building, room, now)) {
            if (schedule.getId() != null) {
                logRepository.save(SysacadScheduleLogEntry.builder()
                        .scheduleId(schedule.getId())
                        .occurredAt(now)
                        .type(ScheduleLogType.SEND_CONFIRMED)
                        .result("CONFIRMED_BY_READ")
                        .previousBuilding(schedule.getKnownBuilding())
                        .previousRoom(schedule.getKnownRoom())
                        .newBuilding(building)
                        .newRoom(room)
                        .attempts(schedule.getAttempts())
                        .build());
            }
            schedule.setKnownBuilding(building);
            schedule.setKnownRoom(room);
            clearDifference(schedule);
            if (schedule.getStatus() == SysacadScheduleStatus.PENDING
                    || schedule.getStatus() == SysacadScheduleStatus.SENDING) {
                schedule.setStatus(SysacadScheduleStatus.SYNCED);
                schedule.setSendingBuilding(null);
                schedule.setSendingRoom(null);
                schedule.setAttempts(0);
                schedule.setNextAttemptAt(null);
                schedule.setLastError(null);
            }
            return ReconcileOutcome.SEND_CONFIRMED;
        }
        if (!runtimeSettings.writeMode().keepsSigaChanges()) {
            schedule.setKnownBuilding(building);
            schedule.setKnownRoom(room);
            schedule.setBaseBuilding(building);
            schedule.setBaseRoom(room);
            clearDifference(schedule);
            return ReconcileOutcome.EXTERNAL_CHANGE_APPLIED;
        }
        boolean sameDifference = Objects.equals(schedule.getDifferentBuilding(), building)
                && Objects.equals(schedule.getDifferentRoom(), room)
                && schedule.getDifferenceDetectedAt() != null;
        if (sameDifference) {
            // Dos lecturas separadas por segundos (la horaria y la previa al envío) no son dos corridas.
            Instant confirmableFrom = schedule.getDifferenceDetectedAt().plus(runtimeSettings.jobDelay());
            return confirmableFrom.isAfter(now) ? ReconcileOutcome.DIFFERENCE_RECORDED
                    : ReconcileOutcome.DIRECT_CHANGE;
        }
        recordDifference(schedule, building, room, now);
        return ReconcileOutcome.DIFFERENCE_RECORDED;
    }

    private static void recordDifference(SysacadSchedule schedule, Integer building, Integer room, Instant now) {
        boolean same = Objects.equals(schedule.getDifferentBuilding(), building)
                && Objects.equals(schedule.getDifferentRoom(), room);
        schedule.setDifferentBuilding(building);
        schedule.setDifferentRoom(room);
        if (!same || schedule.getDifferenceDetectedAt() == null) {
            schedule.setDifferenceDetectedAt(now);
        }
    }

    private static void clearDifference(SysacadSchedule schedule) {
        schedule.setDifferentBuilding(null);
        schedule.setDifferentRoom(null);
        schedule.setDifferenceDetectedAt(null);
    }
}
