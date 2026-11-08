package ar.edu.utn.frc.siga.sysacad.internal.write.state;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.sysacad.api.SysacadScheduleLogEntryDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadScheduleReviewItemDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadScheduleReviewItemDto.ConfirmedSend;
import ar.edu.utn.frc.siga.sysacad.api.SysacadWriteStatusDto;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleLogEntry;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadWriteState;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Waiting;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.SysacadWritePlanner;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.PlannedSchedule;

/** Consultas para la bandeja de revisión de Subsecretaría y el estado del job. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadScheduleQuery {

    private static final int RESPONSE_MAX = 2000;

    private final SysacadScheduleRepository scheduleRepository;
    private final SysacadScheduleLogRepository logRepository;
    private final SysacadWritePlanner planner;
    private final SysacadWriteStateService stateService;
    private final SysacadProperties properties;
    private final Clock clock;

    private enum Type { REVIEW, NOT_SENDABLE, WAITING }

    private record Row(SysacadSchedule schedule, Type type, String reason, Instant since, Waiting waiting) {
    }

    /** Horarios en revisión, no enviables y presentes en la vista, y en espera con la clase a menos de 24 horas. */
    @Transactional(readOnly = true)
    public Page<SysacadScheduleReviewItemDto> findReviewItems(Pageable pageable) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        List<Row> rows = new ArrayList<>();
        for (SysacadSchedule schedule : scheduleRepository.findForReviewList()) {
            if (schedule.getStatus() == SysacadScheduleStatus.REVIEW) {
                rows.add(new Row(schedule, Type.REVIEW, nameOf(schedule.getReviewReason()), schedule.getReviewSince(),
                        null));
            } else {
                rows.add(new Row(schedule, Type.NOT_SENDABLE, nameOf(schedule.getNotSendableReason()), null, null));
            }
        }
        LocalDateTime visibleLimit = now.plus(properties.getWrite().waitingVisibleBefore());
        for (PlannedSchedule planned : planner.plan(now)) {
            if (planned.decision() instanceof Waiting waiting
                    && waiting.date().atTime(waiting.startTime()).isBefore(visibleLimit)) {
                rows.add(new Row(planned.schedule(), Type.WAITING, "REQUEST_ITEM_NOT_ACCEPTED", null, waiting));
            }
        }
        rows.sort(Comparator.comparing(Row::type)
                .thenComparing(Row::since, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> row.schedule().getId()));

        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        List<Row> page = rows.subList(from, to);
        Map<Long, SysacadScheduleLogEntry> confirmed = page.isEmpty() ? Map.of() : Maps.byId(
                logRepository.findLastConfirmedSends(page.stream().map(row -> row.schedule().getId()).toList()),
                SysacadScheduleLogEntry::getScheduleId);
        List<SysacadScheduleReviewItemDto> items = page.stream().map(row -> toItem(row, confirmed)).toList();
        return new PageImpl<>(items, pageable, rows.size());
    }

    @Transactional(readOnly = true)
    public Page<SysacadScheduleLogEntryDto> findLog(Long scheduleId, Pageable pageable) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw ResourceNotFoundException.of("SysacadSchedule", scheduleId);
        }
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id"));
        return logRepository.findByScheduleId(scheduleId, newestFirst).map(SysacadScheduleQuery::toLogDto);
    }

    public SysacadWriteStatusDto writeStatus() {
        SysacadWriteState state = stateService.current();
        return new SysacadWriteStatusDto(properties.getWrite().mode().name(), state.getLastRunStartedAt(),
                state.getLastRunFinishedAt(), state.getLastResult(), state.getLastViewReadAt(),
                state.getOutageSince(), state.getPausedUntil(), state.getLastError(),
                scheduleRepository.countByStatus(SysacadScheduleStatus.REVIEW),
                scheduleRepository.countByStatus(SysacadScheduleStatus.WAITING));
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static SysacadScheduleReviewItemDto toItem(Row row, Map<Long, SysacadScheduleLogEntry> confirmed) {
        SysacadSchedule s = row.schedule();
        SysacadScheduleLogEntry last = confirmed.get(s.getId());
        ConfirmedSend lastSend = last == null ? null
                : new ConfirmedSend(last.getOccurredAt(), last.getNewBuilding(), last.getNewRoom(), last.getResult());
        Long occurrenceId = row.waiting() == null ? null : row.waiting().occurrenceId();
        LocalDate date = row.waiting() == null ? null : row.waiting().date();
        Long itemId = row.waiting() == null ? null : row.waiting().itemId();
        return new SysacadScheduleReviewItemDto(s.getId(), s.getCourseCode(), s.getSubject(), s.getCommission(),
                s.getDayOfWeek(), s.getSemester(), row.type().name(), row.reason(), row.since(), s.getKnownBuilding(),
                s.getKnownRoom(), s.getDifferentBuilding(), s.getDifferentRoom(), lastSend, occurrenceId, date,
                itemId);
    }

    private static SysacadScheduleLogEntryDto toLogDto(SysacadScheduleLogEntry e) {
        String response = e.getResponse() != null && e.getResponse().length() > RESPONSE_MAX
                ? e.getResponse().substring(0, RESPONSE_MAX) : e.getResponse();
        return new SysacadScheduleLogEntryDto(e.getId(), e.getOccurredAt(), e.getType().name(), e.getResult(),
                e.getReason(), e.getPreviousBuilding(), e.getPreviousRoom(), e.getNewBuilding(), e.getNewRoom(),
                e.getSentStartTime(), e.getSentDurationMinutes(), e.getReadStartTime(), e.getReadDurationMinutes(),
                e.getReadEndTime(), e.getRereadStartTime(), e.getRereadDurationMinutes(), e.getRereadEndTime(),
                e.getAttempts(), e.getErrorId(), response, e.getOccurrences(), e.getOccurrenceId(), e.getItemId(),
                e.getRevisionUser(), e.getRevisionOperation(), e.getResolvedBy());
    }
}
