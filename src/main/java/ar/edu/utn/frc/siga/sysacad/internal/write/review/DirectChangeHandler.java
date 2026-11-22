package ar.edu.utn.frc.siga.sysacad.internal.write.review;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeCommand;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeResult;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OccurrenceRequestStateDto;
import ar.edu.utn.frc.siga.roomrequest.model.OccurrenceRequestState;
import ar.edu.utn.frc.siga.roomrequest.service.OccurrenceRequestQuery;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.AffectedOccurrence;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadScheduleStateWriter;

/**
 * Una operación y una transacción por horario: no se envuelve en {@code @AuditOperation} ni en una
 * transacción externa, porque el listener de movimientos descarta los {@code operationId} repetidos.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class DirectChangeHandler {

    private static final Set<Integer> SENTINEL_ROOM_NUMBERS = Set.of(999, 0);
    private static final String SYSACAD_ACTOR = "SYSACAD";
    private static final String DIRECT_CHANGE_OBSERVATION = "Cambio directo en SysAcad";

    private final SysacadScheduleRepository scheduleRepository;
    private final SysacadScheduleStateWriter stateWriter;
    private final ClassroomService classroomService;
    private final OccurrenceService occurrenceService;
    private final AllocationService allocationService;
    private final OccurrenceRequestQuery occurrenceRequestQuery;

    public void handle(Long scheduleId) {
        SysacadSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null || schedule.getStatus() == SysacadScheduleStatus.REVIEW
                || schedule.getDifferentBuilding() == null || schedule.getDifferentRoom() == null) {
            return;
        }
        Integer building = schedule.getDifferentBuilding();
        Integer room = schedule.getDifferentRoom();

        if (SENTINEL_ROOM_NUMBERS.contains(room)) {
            stateWriter.toReview(scheduleId, SysacadReviewReason.NO_ROOM_IN_SYSACAD, List.of());
            return;
        }
        Optional<ClassroomResponseDto> classroom = classroomService.findByRoomNumberAndBuildingCode(room, building);
        if (classroom.isEmpty()) {
            stateWriter.toReview(scheduleId, SysacadReviewReason.ROOM_UNKNOWN_IN_SIGA, List.of());
            return;
        }

        Set<Long> eventIds = eventIdsOf(schedule);
        List<OccurrenceSlotDto> futureClasses = futureClasses(eventIds);
        if (futureClasses.stream().anyMatch(o -> o.status() == OccurrenceStatus.ROOM_RELEASED)) {
            stateWriter.toReview(scheduleId, SysacadReviewReason.RELEASED_FUTURE_CLASS, List.of());
            return;
        }
        List<Long> withRequest = requestedOccurrenceIds(futureClasses);
        if (!withRequest.isEmpty()) {
            stateWriter.toReview(scheduleId, SysacadReviewReason.REQUEST_ON_FUTURE_CLASS, withRequest);
            return;
        }

        SysacadDirectChangeResult result = SystemScope.call(() -> allocationService.applySysacadDirectChange(
                new SysacadDirectChangeCommand(eventIds, classroom.get().id(), DIRECT_CHANGE_OBSERVATION, SYSACAD_ACTOR,
                        false)));
        switch (result) {
            case SysacadDirectChangeResult.Occupied occupied ->
                    stateWriter.toReview(scheduleId, SysacadReviewReason.ROOM_OCCUPIED, occupied.occurrenceIds());
            case SysacadDirectChangeResult.Applied applied -> stateWriter.recordDirectChangeApplied(scheduleId,
                    applied.changes().stream()
                            .filter(change -> change.fromClassroomId() != null)
                            .map(change -> new AffectedOccurrence(change.occurrenceId(), change.date(),
                                    change.fromClassroomId()))
                            .toList());
        }
    }

    private List<OccurrenceSlotDto> futureClasses(Set<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        return occurrenceService.findSlotsByEvents(eventIds, LocalDate.now()).stream()
                .filter(o -> !o.isPast())
                .filter(o -> o.mirrorOfOccurrenceId() == null)
                .toList();
    }

    private List<Long> requestedOccurrenceIds(List<OccurrenceSlotDto> futureClasses) {
        List<Long> ids = futureClasses.stream().map(OccurrenceSlotDto::occurrenceId).toList();
        return occurrenceRequestQuery.findStates(ids).values().stream()
                .filter(s -> s.state() == OccurrenceRequestState.PENDING || s.state() == OccurrenceRequestState.ACCEPTED)
                .map(OccurrenceRequestStateDto::occurrenceId)
                .sorted()
                .toList();
    }

    private static Set<Long> eventIdsOf(SysacadSchedule schedule) {
        return Stream.of(schedule.getEventId1(), schedule.getEventId2())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
