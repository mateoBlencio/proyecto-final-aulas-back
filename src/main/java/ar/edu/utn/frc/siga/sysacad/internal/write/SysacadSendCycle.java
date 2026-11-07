package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationLastRevisionDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.HourFormat;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadWriteClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ScheduleReadResult;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.UpdateRoomResult;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.sync.ReconcileOutcome;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.SendOutcome.Failure;
import ar.edu.utn.frc.siga.sysacad.internal.write.SendOutcome.Kind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.ScheduleTimes;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadScheduleStateWriter;
import ar.edu.utn.frc.siga.sysacad.internal.write.review.DirectChangeHandler;

/**
 * Sin transacción propia: cada paso persiste por {@link SysacadScheduleStateWriter}, así el resultado de un
 * POST no se pierde por un rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadSendCycle {

    static final String NOT_CONFIRMED = "NOT_CONFIRMED";
    static final String SCHEDULE_NOT_FOUND = "SCHEDULE_NOT_FOUND";

    private static final ZoneId SYNC_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Duration AFTER_SYNC_MARGIN = Duration.ofHours(1);
    private static final Duration NO_CRON_DELAY = Duration.ofDays(1);

    private final SysacadWriteClient client;
    private final SysacadCatalogMapper mapper;
    private final SysacadScheduleStateWriter stateWriter;
    private final DirectChangeHandler directChangeHandler;
    private final AllocationService allocationService;
    private final SysacadProperties properties;
    private final Clock clock;

    private record Snapshot(LocalTime startTime, Integer durationMinutes, LocalTime endTime, Integer building,
            Integer room) {

        static Snapshot of(SysacadAllocationDto row) {
            return new Snapshot(row.startTime(), row.durationMinutes(), row.endTime(), row.buildingCode(),
                    row.roomNumber());
        }

        ScheduleTimes times() {
            return new ScheduleTimes(startTime, durationMinutes, endTime);
        }

        boolean sameTimes(Snapshot other) {
            return Objects.equals(startTime, other.startTime) && Objects.equals(durationMinutes, other.durationMinutes)
                    && Objects.equals(endTime, other.endTime);
        }
    }

    private record Reading(List<SysacadAllocationDto> rows, boolean complete, SendOutcome failure) {
    }

    public SendOutcome send(SysacadSchedule schedule, Send decision, RequestPacer pacer) {
        Reading reading = read(schedule, pacer);
        if (reading.failure() != null) {
            return reading.failure();
        }
        List<SysacadAllocationDto> rows = reading.rows();
        if (!reading.complete()) {
            return toReview(schedule, decision, SysacadReviewReason.ROWS_DIVERGED);
        }
        if (rows.isEmpty()) {
            return scheduleNotFound(schedule, "Sin filas en la vista para el horario");
        }
        Set<Snapshot> snapshots = rows.stream().map(Snapshot::of).collect(Collectors.toSet());
        if (snapshots.size() > 1) {
            return toReview(schedule, decision, SysacadReviewReason.ROWS_DIVERGED);
        }
        Snapshot previous = snapshots.iterator().next();

        if (!Objects.equals(previous.building(), schedule.getKnownBuilding())
                || !Objects.equals(previous.room(), schedule.getKnownRoom())) {
            ReconcileOutcome outcome = stateWriter.reconcile(schedule.getId(), previous.building(), previous.room());
            if (outcome == ReconcileOutcome.DIRECT_CHANGE) {
                directChangeHandler.handle(schedule.getId());
            }
            return SendOutcome.of(Kind.SKIPPED);
        }
        if (!hoursMatchSiga(previous, decision)) {
            return toReview(schedule, decision, SysacadReviewReason.HOUR_MISMATCH);
        }

        stateWriter.markIntent(schedule.getId(), decision.building(), decision.room(), decision.occurrenceId(), null,
                previous.times(), previous.startTime(), previous.durationMinutes());
        pacer.acquire();
        UpdateRoomResult result = client.updateRoom(command(schedule, previous, decision.building(), decision.room()));
        return switch (result) {
            case UpdateRoomResult.Applied ignored -> verify(schedule, decision, previous, pacer, null);
            case UpdateRoomResult.ScheduleNotFound notFound -> scheduleNotFound(schedule, notFound.body());
            case UpdateRoomResult.RoomNotFound ignored -> toReview(schedule, decision,
                    SysacadReviewReason.ROOM_NOT_IN_SYSACAD);
            case UpdateRoomResult.Unparseable unparseable -> SendOutcome.failed(new Failure("UNPARSEABLE",
                    "Respuesta no interpretable de ActualizarHorarioAula", unparseable.body(), null));
            case UpdateRoomResult.ServerError error -> SendOutcome.failed(new Failure("SERVER_ERROR",
                    "HTTP " + error.status() + " en ActualizarHorarioAula", error.body(), error.errorId()));
            case UpdateRoomResult.ConnectionFailed failed -> afterConnectionFailure(schedule, decision, previous,
                    pacer, failed.message());
            case UpdateRoomResult.RateLimited limited -> stop(schedule, "RATE_LIMITED", Kind.STOP_RATE_LIMITED,
                    limited.retryAfter());
            case UpdateRoomResult.Unauthorized unauthorized -> stop(schedule,
                    "UNAUTHORIZED_" + unauthorized.status(), Kind.STOP_UNAUTHORIZED, null);
        };
    }

    private SendOutcome verify(SysacadSchedule schedule, Send decision, Snapshot previous, RequestPacer pacer,
            String connectionMessage) {
        Reading reread = read(schedule, pacer);
        if (reread.failure() != null) {
            return switch (reread.failure().kind()) {
                case STOP_RATE_LIMITED -> stop(schedule, "RATE_LIMITED", Kind.STOP_RATE_LIMITED,
                        reread.failure().retryAfter());
                case STOP_UNAUTHORIZED -> stop(schedule, "UNAUTHORIZED", Kind.STOP_UNAUTHORIZED, null);
                default -> stop(schedule, "CONNECTION_FAILED", Kind.STOP_CONNECTION, null);
            };
        }
        List<Snapshot> snapshots = reread.rows().stream().map(Snapshot::of).toList();
        Snapshot changed = snapshots.stream().filter(s -> !s.sameTimes(previous)).findFirst().orElse(null);
        if (changed != null) {
            return revert(schedule, decision, previous, changed.times(), pacer);
        }
        boolean confirmed = reread.complete() && !snapshots.isEmpty() && snapshots.stream()
                .allMatch(s -> Objects.equals(s.building(), decision.building())
                        && Objects.equals(s.room(), decision.room()));
        if (confirmed) {
            AllocationLastRevisionDto revision = allocationService.findLastRevision(decision.occurrenceId())
                    .orElse(null);
            stateWriter.confirm(schedule.getId(), decision.occurrenceId(), null,
                    revision == null ? null : revision.user(), revision == null ? null : revision.operationId(),
                    snapshots.get(0).times());
            return SendOutcome.of(Kind.CONFIRMED);
        }
        if (connectionMessage != null) {
            return stop(schedule, "CONNECTION_FAILED", Kind.STOP_CONNECTION, null);
        }
        if (NOT_CONFIRMED.equals(schedule.getLastError())) {
            return toReview(schedule, decision, SysacadReviewReason.NOT_CONFIRMED);
        }
        return SendOutcome.failed(new Failure(NOT_CONFIRMED, NOT_CONFIRMED,
                "La vista no muestra el aula enviada tras la relectura", null));
    }

    private SendOutcome afterConnectionFailure(SysacadSchedule schedule, Send decision, Snapshot previous,
            RequestPacer pacer, String message) {
        log.warn("Se cortó la conexión al enviar el horario {}: se relee la vista antes de reintentar: {}",
                schedule.getId(), message);
        return verify(schedule, decision, previous, pacer, message);
    }

    /** Devuelve a SysAcad los valores previos completos: hora, duración, edificio y aula. */
    private SendOutcome revert(SysacadSchedule schedule, Send decision, Snapshot previous, ScheduleTimes reread,
            RequestPacer pacer) {
        stateWriter.markRevertIntent(schedule.getId(), previous.building(), previous.room(), decision.occurrenceId(),
                previous.times(), previous.startTime(), previous.durationMinutes());
        pacer.acquire();
        UpdateRoomResult result = client.updateRoom(command(schedule, previous, previous.building(), previous.room()));
        if (!(result instanceof UpdateRoomResult.Applied)) {
            log.error("No se pudieron reenviar los valores previos del horario {}: {}", schedule.getId(), result);
        }
        stateWriter.toReviewAfterRevert(schedule.getId(), List.of(decision.occurrenceId()), reread,
                resultName(result));
        return SendOutcome.hoursChanged();
    }

    private static String resultName(UpdateRoomResult result) {
        return result.getClass().getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private SendOutcome scheduleNotFound(SysacadSchedule schedule, String body) {
        if (SCHEDULE_NOT_FOUND.equals(schedule.getLastError())) {
            stateWriter.toReview(schedule.getId(), SysacadReviewReason.SCHEDULE_NOT_FOUND, List.of());
            return SendOutcome.review(SysacadReviewReason.SCHEDULE_NOT_FOUND);
        }
        stateWriter.recordFailure(schedule.getId(), SCHEDULE_NOT_FOUND, false, afterNextSync(), SCHEDULE_NOT_FOUND,
                body, null);
        return SendOutcome.of(Kind.SKIPPED);
    }

    private Instant afterNextSync() {
        Instant now = clock.instant();
        String cron = properties.getSync().cron();
        if (cron == null) {
            return now.plus(NO_CRON_DELAY);
        }
        ZonedDateTime next = CronExpression.parse(cron).next(now.atZone(SYNC_ZONE));
        return next == null ? now.plus(NO_CRON_DELAY) : next.toInstant().plus(AFTER_SYNC_MARGIN);
    }

    private SendOutcome stop(SysacadSchedule schedule, String error, Kind kind, Duration retryAfter) {
        stateWriter.recordFailure(schedule.getId(), error, false, null, error, null, null);
        return new SendOutcome(kind, retryAfter, null, null);
    }

    private SendOutcome toReview(SysacadSchedule schedule, Send decision, SysacadReviewReason reason) {
        stateWriter.toReview(schedule.getId(), reason, List.of(decision.occurrenceId()));
        return SendOutcome.review(reason);
    }

    private boolean hoursMatchSiga(Snapshot read, Send decision) {
        if (decision.endTime() == null || read.durationMinutes() == null) {
            return false;
        }
        long sigaMinutes = Duration.between(decision.startTime(), decision.endTime()).toMinutes();
        return Objects.equals(read.startTime(), decision.startTime()) && read.durationMinutes() == sigaMinutes;
    }

    private Reading read(SysacadSchedule schedule, RequestPacer pacer) {
        pacer.acquire();
        ScheduleReadResult result = client.readSchedule(schedule.getSpecialty(), schedule.getPlan(),
                schedule.getSubject());
        return switch (result) {
            case ScheduleReadResult.Rows found -> keyRows(schedule, found.rows());
            case ScheduleReadResult.ConnectionFailed ignored ->
                    new Reading(null, false, SendOutcome.of(Kind.STOP_CONNECTION));
            case ScheduleReadResult.RateLimited limited ->
                    new Reading(null, false, SendOutcome.rateLimited(limited.retryAfter()));
            case ScheduleReadResult.Unauthorized ignored ->
                    new Reading(null, false, SendOutcome.of(Kind.STOP_UNAUTHORIZED));
            case ScheduleReadResult.ServerError error -> new Reading(null, false, SendOutcome.failed(new Failure(
                    "READ_SERVER_ERROR", "HTTP " + error.status() + " al leer la vista", error.body(),
                    error.errorId())));
        };
    }

    private Reading keyRows(SysacadSchedule schedule, List<RawSchedule> rows) {
        List<RawSchedule> ofKey = rows.stream()
                .filter(row -> Objects.equals(row.comision(), schedule.getCommission())
                        && Objects.equals(row.dia(), schedule.getDayOfWeek())
                        && Objects.equals(row.horarioCuatrimestre(), schedule.getSemester()))
                .toList();
        List<SysacadAllocationDto> mapped = ofKey.stream()
                .map(mapper::toAllocation)
                .filter(Objects::nonNull)
                .toList();
        return new Reading(mapped, mapped.size() == ofKey.size(), null);
    }

    private static UpdateRoomCommand command(SysacadSchedule schedule, Snapshot times, int building, int room) {
        return new UpdateRoomCommand(schedule.getYear(), schedule.getSpecialty(), schedule.getPlan(),
                schedule.getSubject(), schedule.getCommission(), schedule.getDayOfWeek(), schedule.getSemester(),
                HourFormat.toProcedureHour(times.startTime().toString()), times.durationMinutes(), building, room);
    }
}
