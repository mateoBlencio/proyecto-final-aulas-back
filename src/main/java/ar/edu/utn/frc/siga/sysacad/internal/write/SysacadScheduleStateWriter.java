package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleLogEntry;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.sync.ReconcileOutcome;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadViewReconciler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Cambios de estado de un horario más su fila de registro, cada uno en su propia transacción: una intención
 * o un resultado sobreviven al rollback de cualquier otra cosa.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadScheduleStateWriter {

    private static final int REASON_MAX = 255;
    private static final int LAST_ERROR_MAX = 1000;
    private static final int ERROR_ID_MAX = 100;
    private static final int RESULT_MAX = 40;

    private final SysacadScheduleRepository scheduleRepository;
    private final SysacadScheduleLogRepository logRepository;
    private final SysacadViewReconciler reconciler;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReconcileOutcome reconcile(Long scheduleId, Integer building, Integer room) {
        return reconciler.reconcile(load(scheduleId), building, room, clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIntent(Long scheduleId, Integer building, Integer room, Long occurrenceId, Long itemId,
            ScheduleTimes read, LocalTime sentStartTime, Integer sentDurationMinutes) {
        SysacadSchedule schedule = load(scheduleId);
        schedule.setStatus(SysacadScheduleStatus.SENDING);
        schedule.setSendingBuilding(building);
        schedule.setSendingRoom(room);
        logRepository.save(entry(schedule, ScheduleLogType.SEND_INTENT)
                .previousBuilding(schedule.getKnownBuilding())
                .previousRoom(schedule.getKnownRoom())
                .newBuilding(building)
                .newRoom(room)
                .occurrenceId(occurrenceId)
                .itemId(itemId)
                .sentStartTime(sentStartTime)
                .sentDurationMinutes(sentDurationMinutes)
                .readStartTime(read.startTime())
                .readDurationMinutes(read.durationMinutes())
                .readEndTime(read.endTime())
                .attempts(schedule.getAttempts())
                .build());
    }

    /** Intención de la reversión: deja los valores previos en el registro sin tocar el estado del horario. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRevertIntent(Long scheduleId, Integer building, Integer room, Long occurrenceId,
            ScheduleTimes read, LocalTime sentStartTime, Integer sentDurationMinutes) {
        SysacadSchedule schedule = load(scheduleId);
        logRepository.save(entry(schedule, ScheduleLogType.SEND_INTENT)
                .previousBuilding(schedule.getKnownBuilding())
                .previousRoom(schedule.getKnownRoom())
                .newBuilding(building)
                .newRoom(room)
                .occurrenceId(occurrenceId)
                .sentStartTime(sentStartTime)
                .sentDurationMinutes(sentDurationMinutes)
                .readStartTime(read.startTime())
                .readDurationMinutes(read.durationMinutes())
                .readEndTime(read.endTime())
                .attempts(schedule.getAttempts())
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(Long scheduleId, Long occurrenceId, Long itemId, String revisionUser,
            String revisionOperation, ScheduleTimes reread) {
        SysacadSchedule schedule = load(scheduleId);
        Integer building = schedule.getSendingBuilding();
        Integer room = schedule.getSendingRoom();
        if (building == null || room == null) {
            return;
        }
        logRepository.save(entry(schedule, ScheduleLogType.SEND_CONFIRMED)
                .previousBuilding(schedule.getKnownBuilding())
                .previousRoom(schedule.getKnownRoom())
                .result("CONFIRMED")
                .newBuilding(building)
                .newRoom(room)
                .occurrenceId(occurrenceId)
                .itemId(itemId)
                .revisionUser(truncate(revisionUser, 150))
                .revisionOperation(truncate(revisionOperation, 100))
                .rereadStartTime(reread.startTime())
                .rereadDurationMinutes(reread.durationMinutes())
                .rereadEndTime(reread.endTime())
                .attempts(schedule.getAttempts())
                .build());
        schedule.setKnownBuilding(building);
        schedule.setKnownRoom(room);
        clearSend(schedule);
        schedule.setStatus(SysacadScheduleStatus.SYNCED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long scheduleId, String result, boolean countAttempt, Instant nextAttemptAt,
            String error, String response, String errorId) {
        SysacadSchedule schedule = load(scheduleId);
        if (countAttempt) {
            schedule.setAttempts(schedule.getAttempts() + 1);
        }
        schedule.setNextAttemptAt(nextAttemptAt);
        schedule.setLastError(truncate(error, LAST_ERROR_MAX));
        schedule.setStatus(SysacadScheduleStatus.PENDING);
        logRepository.save(entry(schedule, ScheduleLogType.SEND_FAILED)
                .previousBuilding(schedule.getKnownBuilding())
                .previousRoom(schedule.getKnownRoom())
                .result(truncate(result, RESULT_MAX))
                .reason(truncate(error, REASON_MAX))
                .newBuilding(schedule.getSendingBuilding())
                .newRoom(schedule.getSendingRoom())
                .response(response)
                .errorId(truncate(errorId, ERROR_ID_MAX))
                .attempts(schedule.getAttempts())
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void toReview(Long scheduleId, SysacadReviewReason reason, Collection<Long> occurrenceIds) {
        openReview(scheduleId, reason, occurrenceIds, null, null);
    }

    /** Pasa a revisión por horas cambiadas tras un envío: guarda las horas releídas y el resultado del reenvío. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void toReviewAfterRevert(Long scheduleId, Collection<Long> occurrenceIds, ScheduleTimes reread,
            String revertResult) {
        openReview(scheduleId, SysacadReviewReason.HOURS_CHANGED_AFTER_SEND, occurrenceIds, reread, revertResult);
    }

    private void openReview(Long scheduleId, SysacadReviewReason reason, Collection<Long> occurrenceIds,
            ScheduleTimes reread, String result) {
        SysacadSchedule schedule = load(scheduleId);
        if (schedule.getStatus() == SysacadScheduleStatus.REVIEW) {
            return;
        }
        Instant now = clock.instant();
        schedule.setStatus(SysacadScheduleStatus.REVIEW);
        schedule.setReviewReason(reason);
        schedule.setReviewSince(now);
        schedule.setNextAttemptAt(null);
        Integer newBuilding = schedule.getDifferentBuilding() != null
                ? schedule.getDifferentBuilding() : schedule.getSendingBuilding();
        Integer newRoom = schedule.getDifferentRoom() != null
                ? schedule.getDifferentRoom() : schedule.getSendingRoom();
        SysacadScheduleLogEntry.SysacadScheduleLogEntryBuilder entry = entry(schedule, ScheduleLogType.REVIEW_OPENED)
                .reason(reason.name())
                .result(truncate(result, RESULT_MAX))
                .newBuilding(newBuilding)
                .newRoom(newRoom)
                .occurrences(occurrenceIds == null || occurrenceIds.isEmpty() ? null : json(occurrenceIds))
                .attempts(schedule.getAttempts());
        if (reread != null) {
            entry.rereadStartTime(reread.startTime())
                    .rereadDurationMinutes(reread.durationMinutes())
                    .rereadEndTime(reread.endTime());
        }
        logRepository.save(entry.build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void toWaiting(Long scheduleId, Long occurrenceId, Long itemId) {
        SysacadSchedule schedule = load(scheduleId);
        if (schedule.getStatus() == SysacadScheduleStatus.WAITING) {
            return;
        }
        schedule.setStatus(SysacadScheduleStatus.WAITING);
        logRepository.save(entry(schedule, ScheduleLogType.WAITING)
                .occurrenceId(occurrenceId)
                .itemId(itemId)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearWaiting(Long scheduleId) {
        SysacadSchedule schedule = load(scheduleId);
        if (schedule.getStatus() == SysacadScheduleStatus.WAITING) {
            schedule.setStatus(SysacadScheduleStatus.SYNCED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDirectChangeApplied(Long scheduleId, List<AffectedOccurrence> annulled) {
        SysacadSchedule schedule = load(scheduleId);
        Integer building = schedule.getDifferentBuilding();
        Integer room = schedule.getDifferentRoom();
        logRepository.save(entry(schedule, ScheduleLogType.DIRECT_CHANGE_DETECTED)
                .previousBuilding(schedule.getKnownBuilding())
                .previousRoom(schedule.getKnownRoom())
                .newBuilding(building)
                .newRoom(room)
                .build());
        if (!annulled.isEmpty()) {
            logRepository.save(entry(schedule, ScheduleLogType.SIGA_CHANGES_ANNULLED)
                    .previousBuilding(schedule.getKnownBuilding())
                    .previousRoom(schedule.getKnownRoom())
                    .newBuilding(building)
                    .newRoom(room)
                    .occurrences(json(annulled))
                    .build());
        }
        schedule.setKnownBuilding(building);
        schedule.setKnownRoom(room);
        schedule.setBaseBuilding(building);
        schedule.setBaseRoom(room);
        schedule.setDifferentBuilding(null);
        schedule.setDifferentRoom(null);
        schedule.setDifferenceDetectedAt(null);
        clearSend(schedule);
        schedule.setStatus(SysacadScheduleStatus.SYNCED);
    }

    /** Reenviar lo de SIGA: la última lectura pasa a valor conocido y el job recalcula y envía en la corrida siguiente. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolveResendSiga(Long scheduleId, String user) {
        SysacadSchedule schedule = load(scheduleId);
        Integer building = lastReadBuilding(schedule);
        Integer room = lastReadRoom(schedule);
        logResolved(schedule, "RESEND_SIGA", user, building, room);
        schedule.setKnownBuilding(building);
        schedule.setKnownRoom(room);
        closeReview(schedule);
    }

    /** Quedarse con lo de SysAcad: la última lectura pasa a valor conocido y a aula base. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolveKeepSysacad(Long scheduleId, String user, List<AffectedOccurrence> annulled) {
        SysacadSchedule schedule = load(scheduleId);
        Integer building = lastReadBuilding(schedule);
        Integer room = lastReadRoom(schedule);
        if (!annulled.isEmpty()) {
            logRepository.save(entry(schedule, ScheduleLogType.SIGA_CHANGES_ANNULLED)
                    .previousBuilding(schedule.getKnownBuilding())
                    .previousRoom(schedule.getKnownRoom())
                    .newBuilding(building)
                    .newRoom(room)
                    .occurrences(json(annulled))
                    .build());
        }
        logResolved(schedule, "KEEP_SYSACAD", user, building, room);
        schedule.setKnownBuilding(building);
        schedule.setKnownRoom(room);
        schedule.setBaseBuilding(building);
        schedule.setBaseRoom(room);
        closeReview(schedule);
    }

    public static Integer lastReadBuilding(SysacadSchedule schedule) {
        return schedule.getDifferentBuilding() != null ? schedule.getDifferentBuilding() : schedule.getKnownBuilding();
    }

    public static Integer lastReadRoom(SysacadSchedule schedule) {
        return schedule.getDifferentRoom() != null ? schedule.getDifferentRoom() : schedule.getKnownRoom();
    }

    private void logResolved(SysacadSchedule schedule, String action, String user, Integer building, Integer room) {
        logRepository.save(entry(schedule, ScheduleLogType.REVIEW_RESOLVED)
                .reason(action)
                .resolvedBy(truncate(user, 150))
                .previousBuilding(schedule.getKnownBuilding())
                .previousRoom(schedule.getKnownRoom())
                .newBuilding(building)
                .newRoom(room)
                .build());
    }

    private static void closeReview(SysacadSchedule schedule) {
        schedule.setDifferentBuilding(null);
        schedule.setDifferentRoom(null);
        schedule.setDifferenceDetectedAt(null);
        clearSend(schedule);
        schedule.setReviewReason(null);
        schedule.setReviewSince(null);
        schedule.setStatus(SysacadScheduleStatus.SYNCED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDirectChangeDryRun(Long scheduleId, Integer fromBuilding, Integer fromRoom,
            Integer toBuilding, Integer toRoom, List<AffectedOccurrence> wouldAnnul) {
        SysacadSchedule schedule = load(scheduleId);
        logRepository.save(entry(schedule, ScheduleLogType.DIRECT_CHANGE_DRY_RUN)
                .previousBuilding(fromBuilding)
                .previousRoom(fromRoom)
                .newBuilding(toBuilding)
                .newRoom(toRoom)
                .occurrences(wouldAnnul.isEmpty() ? null : json(wouldAnnul))
                .build());
    }

    /**
     * Fila suelta de un evento sin cambio de estado (DRY_RUN_SEND, NO_ROOM_AFTER_CANCEL,
     * DESTINATION_NOT_SENDABLE). No repite la fila si la última de ese tipo del horario dice lo mismo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(Long scheduleId, ScheduleLogType type, Integer building, Integer room,
            Long occurrenceId, Long itemId, String reason) {
        SysacadSchedule schedule = load(scheduleId);
        String truncatedReason = truncate(reason, REASON_MAX);
        boolean repeated = logRepository.findFirstByScheduleIdAndTypeOrderByIdDesc(scheduleId, type)
                .filter(last -> Objects.equals(last.getNewBuilding(), building)
                        && Objects.equals(last.getNewRoom(), room)
                        && Objects.equals(last.getOccurrenceId(), occurrenceId)
                        && Objects.equals(last.getItemId(), itemId)
                        && Objects.equals(last.getReason(), truncatedReason))
                .isPresent();
        if (repeated) {
            return;
        }
        logRepository.save(entry(schedule, type)
                .previousBuilding(schedule.getKnownBuilding())
                .previousRoom(schedule.getKnownRoom())
                .newBuilding(building)
                .newRoom(room)
                .occurrenceId(occurrenceId)
                .itemId(itemId)
                .reason(truncatedReason)
                .build());
    }

    private SysacadSchedule load(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ResourceNotFoundException.of("SysacadSchedule", scheduleId));
    }

    private static void clearSend(SysacadSchedule schedule) {
        schedule.setSendingBuilding(null);
        schedule.setSendingRoom(null);
        schedule.setAttempts(0);
        schedule.setNextAttemptAt(null);
        schedule.setLastError(null);
    }

    private SysacadScheduleLogEntry.SysacadScheduleLogEntryBuilder entry(SysacadSchedule schedule,
            ScheduleLogType type) {
        return SysacadScheduleLogEntry.builder()
                .scheduleId(schedule.getId())
                .occurredAt(clock.instant())
                .type(type);
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
