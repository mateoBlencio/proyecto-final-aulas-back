package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadWriteState;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.sync.SysacadScheduleLoader.DryRunChange;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.NoRoom;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.NoRoomAfterCancel;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.NotSendable;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.NextClassDecision.Waiting;
import ar.edu.utn.frc.siga.sysacad.internal.write.RunReport.ClassWithoutRoom;
import ar.edu.utn.frc.siga.sysacad.internal.write.RunReport.Result;
import ar.edu.utn.frc.siga.sysacad.internal.write.RunReport.ReviewedSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.SendOutcome.Failure;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadViewReader.ViewReadResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadWriteRun {

    private final SysacadWriteStateService stateService;
    private final SysacadViewReader viewReader;
    private final SysacadWritePlanner planner;
    private final SysacadSendCycle sendCycle;
    private final SysacadScheduleStateWriter scheduleWriter;
    private final DirectChangeHandler directChangeHandler;
    private final SysacadScheduleRepository scheduleRepository;
    private final SysacadProperties properties;
    private final Clock clock;

    private record PendingFailure(SysacadSchedule schedule, Failure failure) {
    }

    private static final class Execution {
        final List<ReviewedSchedule> reviewed = new ArrayList<>();
        final List<ClassWithoutRoom> classesWithoutRoom = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<PendingFailure> failures = new ArrayList<>();
        boolean contactedSysacad;
        Result result = Result.OK;
    }

    public RunReport run(Instant now) {
        // Un horario con las horas cambiadas tras un envío frena el job: seguir enviando arriesga más horarios.
        if (scheduleRepository.existsByStatusAndReviewReason(SysacadScheduleStatus.REVIEW,
                SysacadReviewReason.HOURS_CHANGED_AFTER_SEND)) {
            stateService.finishRun(clock.instant(), Result.SKIPPED_HOURS_CHANGED.name(),
                    "Job frenado: un horario cambió de hora tras un envío y espera revisión");
            return new RunReport(Result.SKIPPED_HOURS_CHANGED, List.of(), List.of(), List.of());
        }
        SysacadWriteState state = stateService.current();
        if (state.getPausedUntil() != null && state.getPausedUntil().isAfter(now)) {
            return new RunReport(Result.SKIPPED_PAUSED, List.of(), List.of(), List.of());
        }
        stateService.startRun(now);
        Execution execution = new Execution();
        try {
            execute(now, state, execution);
        } catch (RuntimeException e) {
            log.error("Falló la corrida de envío de aulas a SysAcad", e);
            execution.errors.add(e.toString());
            execution.result = Result.ERROR;
        }
        stateService.finishRun(clock.instant(), execution.result.name(),
                execution.errors.isEmpty() ? null : String.join("; ", execution.errors));
        return new RunReport(execution.result, execution.reviewed, execution.classesWithoutRoom, execution.errors);
    }

    private void execute(Instant now, SysacadWriteState state, Execution execution) {
        SysacadProperties.Write config = properties.getWrite();
        boolean viewDue = state.getLastViewReadAt() == null
                || !now.isBefore(state.getLastViewReadAt().plus(config.viewReadInterval()));
        if (viewDue && !readView(now, execution)) {
            return;
        }

        List<PlannedSchedule> plan = planner.plan(LocalDateTime.ofInstant(now, clock.getZone()));
        clearStaleWaiting(plan);

        RequestPacer pacer = RequestPacer.sleepingThread(clock, config.maxRequestsPerMinute());
        int sends = 0;
        for (PlannedSchedule planned : plan) {
            SysacadSchedule schedule = planned.schedule();
            try {
                switch (planned.decision()) {
                    case Waiting waiting -> scheduleWriter.toWaiting(schedule.getId(), waiting.occurrenceId(),
                            waiting.itemId());
                    case NoRoomAfterCancel cancelled -> {
                        scheduleWriter.recordEvent(schedule.getId(), ScheduleLogType.NO_ROOM_AFTER_CANCEL, null, null,
                                cancelled.occurrenceId(), cancelled.itemId(), null);
                        execution.classesWithoutRoom.add(
                                new ClassWithoutRoom(schedule.getId(), cancelled.occurrenceId(), cancelled.date()));
                    }
                    case NotSendable notSendable -> scheduleWriter.recordEvent(schedule.getId(),
                            ScheduleLogType.DESTINATION_NOT_SENDABLE, null, null, notSendable.occurrenceId(), null,
                            notSendable.reason().name());
                    case Send send when config.mode() == SysacadWriteMode.DRY_RUN -> scheduleWriter.recordEvent(
                            schedule.getId(), ScheduleLogType.DRY_RUN_SEND, send.building(), send.room(),
                            send.occurrenceId(), null, null);
                    case Send send -> {
                        if (config.mode() != SysacadWriteMode.ON
                                || schedule.getNextAttemptAt() != null && schedule.getNextAttemptAt().isAfter(now)
                                || sends >= config.maxSendsPerRun()) {
                            continue;
                        }
                        sends++;
                        if (handle(schedule, sendCycle.send(schedule, send, pacer), now, execution)) {
                            return;
                        }
                    }
                    case NoRoom ignored -> {
                    }
                    default -> {
                    }
                }
            } catch (RuntimeException e) {
                log.error("Falló el procesamiento del horario {}", schedule.getId(), e);
                execution.errors.add("Horario " + schedule.getId() + ": " + e);
            }
        }
        flushFailures(execution, true, now);
        if (execution.contactedSysacad) {
            stateService.clearOutage();
        }
    }

    /** Devuelve false si la corrida debe cortar. */
    private boolean readView(Instant now, Execution execution) {
        ViewReadResult result;
        try {
            result = viewReader.read();
        } catch (RuntimeException e) {
            log.error("No se pudo leer la vista de horarios de SysAcad", e);
            execution.errors.add("Lectura de la vista: " + e);
            execution.result = Result.VIEW_READ_FAILED;
            stateService.markOutage(now);
            return false;
        }
        stateService.recordViewRead(now);
        execution.contactedSysacad = true;
        for (Long scheduleId : result.directChangeScheduleIds()) {
            try {
                directChangeHandler.handle(scheduleId);
            } catch (RuntimeException e) {
                log.error("No se pudo procesar el cambio directo del horario {}", scheduleId, e);
                execution.errors.add("Cambio directo del horario " + scheduleId + ": " + e);
            }
        }
        for (DryRunChange change : result.dryRunChanges()) {
            try {
                directChangeHandler.recordDryRun(change.scheduleId(), change.fromBuilding(), change.fromRoom(),
                        change.toBuilding(), change.toRoom());
            } catch (RuntimeException e) {
                log.error("No se pudo registrar el cambio directo simulado del horario {}", change.scheduleId(), e);
                execution.errors.add("Cambio directo simulado del horario " + change.scheduleId() + ": " + e);
            }
        }
        return true;
    }

    private void clearStaleWaiting(List<PlannedSchedule> plan) {
        Set<Long> waitingIds = plan.stream()
                .filter(p -> p.decision() instanceof Waiting)
                .map(p -> p.schedule().getId())
                .collect(Collectors.toSet());
        for (SysacadSchedule schedule : scheduleRepository.findByStatus(SysacadScheduleStatus.WAITING)) {
            if (!waitingIds.contains(schedule.getId())) {
                try {
                    scheduleWriter.clearWaiting(schedule.getId());
                } catch (RuntimeException e) {
                    log.error("No se pudo limpiar la espera del horario {}", schedule.getId(), e);
                }
            }
        }
    }

    /** Devuelve true si la corrida debe cortar. */
    private boolean handle(SysacadSchedule schedule, SendOutcome outcome, Instant now, Execution execution) {
        SysacadProperties.Write config = properties.getWrite();
        if (outcome.kind() == SendOutcome.Kind.SCHEDULE_FAILED) {
            execution.failures.add(new PendingFailure(schedule, outcome.failure()));
            if (execution.failures.size() >= config.maxConsecutiveFailures()) {
                flushFailures(execution, false, now);
                stateService.markOutage(now);
                execution.result = Result.STOPPED_FAILURES;
                return true;
            }
            return false;
        }
        boolean generalFailure = outcome.kind() == SendOutcome.Kind.STOP_CONNECTION
                || outcome.kind() == SendOutcome.Kind.STOP_RATE_LIMITED
                || outcome.kind() == SendOutcome.Kind.STOP_UNAUTHORIZED;
        flushFailures(execution, !generalFailure, now);
        switch (outcome.kind()) {
            case CONFIRMED, SKIPPED -> execution.contactedSysacad = true;
            case REVIEW -> {
                execution.contactedSysacad = true;
                execution.reviewed.add(new ReviewedSchedule(schedule.getId(), outcome.reviewReason()));
            }
            case STOP_HOURS_CHANGED -> {
                execution.reviewed.add(new ReviewedSchedule(schedule.getId(), outcome.reviewReason()));
                execution.result = Result.STOPPED_HOURS_CHANGED;
                return true;
            }
            case STOP_RATE_LIMITED -> {
                Duration pause = outcome.retryAfter() != null ? outcome.retryAfter() : config.jobDelay();
                stateService.pauseUntil(clock.instant().plus(pause));
                execution.result = Result.STOPPED_RATE_LIMITED;
                return true;
            }
            case STOP_CONNECTION -> {
                stateService.markOutage(now);
                execution.result = Result.STOPPED_CONNECTION;
                return true;
            }
            case STOP_UNAUTHORIZED -> {
                stateService.markOutage(now);
                execution.result = Result.STOPPED_UNAUTHORIZED;
                return true;
            }
            case SCHEDULE_FAILED -> throw new IllegalStateException("Fallo de horario ya tratado");
        }
        return false;
    }

    private void flushFailures(Execution execution, boolean countAttempt, Instant now) {
        SysacadProperties.Write config = properties.getWrite();
        List<Duration> backoff = config.retryBackoff();
        for (PendingFailure pending : execution.failures) {
            SysacadSchedule schedule = pending.schedule();
            Failure failure = pending.failure();
            try {
                int attempts = schedule.getAttempts() + (countAttempt ? 1 : 0);
                boolean exhausted = countAttempt && attempts >= config.maxAttempts();
                Instant nextAttemptAt = exhausted ? null
                        : clock.instant().plus(backoff.get(countAttempt ? Math.min(attempts, backoff.size()) - 1 : 0));
                scheduleWriter.recordFailure(schedule.getId(), failure.result(), countAttempt, nextAttemptAt,
                        failure.error(), failure.response(), failure.errorId());
                if (exhausted) {
                    scheduleWriter.toReview(schedule.getId(), SysacadReviewReason.MAX_ATTEMPTS, List.of());
                    execution.reviewed.add(new ReviewedSchedule(schedule.getId(), SysacadReviewReason.MAX_ATTEMPTS));
                }
            } catch (RuntimeException e) {
                log.error("No se pudo registrar el fallo del horario {}", schedule.getId(), e);
                execution.errors.add("Fallo del horario " + schedule.getId() + ": " + e);
            }
        }
        execution.failures.clear();
    }
}
