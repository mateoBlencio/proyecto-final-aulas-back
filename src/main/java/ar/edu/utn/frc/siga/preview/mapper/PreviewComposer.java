package ar.edu.utn.frc.siga.preview.mapper;

import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.Overcrowding;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewItemDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.UnresolvedAllocationDto;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator.ResolvedProposal;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PreviewComposer {

    private final ClassroomService classroomService;
    private final PreviewValidator previewValidator;

    public PreviewResponseDto compose(OptimizationResult preview, List<RecurringEventResponseDto> events,
                                            Map<Long, List<LocalDate>> datesByEvent,
                                            Map<Long, Long> priorRoomByEvent,
                                            List<OptimizerRoom> rooms, List<OccupiedSlot> databaseOccupancy) {
        Map<Long, RecurringEventResponseDto> eventsById = Maps.byId(events, RecurringEventResponseDto::id);

        List<OptimizerAllocation> resolved = new ArrayList<>();
        List<OptimizerAllocation> unresolved = new ArrayList<>();
        Map<String, Long> effectiveRoomByEventId = new LinkedHashMap<>();
        for (OptimizerAllocation allocation : preview.allocations()) {
            Long classroomId = allocation.classroomId();
            if (classroomId == null) {
                classroomId = priorRoomByEvent.get(eventIdOf(allocation));
            }
            if (classroomId != null) {
                resolved.add(allocation);
                effectiveRoomByEventId.put(allocation.eventId(), classroomId);
            } else {
                unresolved.add(allocation);
            }
        }

        List<AcademicEventResponseDto> referencedEvents = preview.allocations().stream()
                .<AcademicEventResponseDto>map(a -> eventsById.get(eventIdOf(a)))
                .filter(Objects::nonNull)
                .toList();
        Map<Long, AcademicEventResponseDto> eventDtoById = Maps.byId(referencedEvents, AcademicEventResponseDto::id);

        Set<Long> classroomIds = Set.copyOf(effectiveRoomByEventId.values());
        Map<Long, ClassroomResponseDto> classroomDtoById = Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);

        List<PreviewItemDto> allocations = resolved.stream()
                .map(a -> toPreviewItemDto(a, eventDtoById, datesByEvent,
                        classroomDtoById.get(effectiveRoomByEventId.get(a.eventId())), priorRoomByEvent))
                .toList();

        Set<Long> candidateRoomIds = rooms.stream().map(OptimizerRoom::id).collect(Collectors.toSet());
        List<ResolvedProposal> resolvedProposals = buildResolvedProposals(effectiveRoomByEventId, eventsById, datesByEvent);
        List<UnresolvedAllocationDto> unresolvedDtos = unresolved.stream()
                .map(a -> toUnresolvedAllocationDto(a, eventDtoById, datesByEvent, eventsById,
                        candidateRoomIds, databaseOccupancy, resolvedProposals))
                .toList();

        return new PreviewResponseDto(preview.previewId(), allocations, unresolvedDtos);
    }

    private List<ResolvedProposal> buildResolvedProposals(Map<String, Long> effectiveRoomByEventId,
            Map<Long, RecurringEventResponseDto> eventsById, Map<Long, List<LocalDate>> datesByEvent) {
        List<ResolvedProposal> proposals = new ArrayList<>();
        for (Map.Entry<String, Long> entry : effectiveRoomByEventId.entrySet()) {
            Long eventId = Long.valueOf(entry.getKey());
            RecurringEventResponseDto event = eventsById.get(eventId);
            if (event == null) continue;
            proposals.add(new ResolvedProposal(eventId, entry.getValue(),
                    datesByEvent.getOrDefault(eventId, List.of()), event.startTime(), event.endTime()));
        }
        return proposals;
    }

    private PreviewItemDto toPreviewItemDto(OptimizerAllocation allocation,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Long, List<LocalDate>> datesByEvent,
            ClassroomResponseDto classroom, Map<Long, Long> priorRoomByEvent) {
        Long eventId = eventIdOf(allocation);
        AcademicEventResponseDto event = eventDtoById.get(eventId);
        boolean unchanged = classroom != null && Objects.equals(classroom.id(), priorRoomByEvent.get(eventId));
        return new PreviewItemDto(
                event, datesByEvent.getOrDefault(eventId, List.of()), classroom,
                Objects.requireNonNullElse(Overcrowding.by(
                        event != null ? event.enrolled() : null, classroom != null ? classroom.capacity() : null), 0),
                unchanged);
    }

    private UnresolvedAllocationDto toUnresolvedAllocationDto(OptimizerAllocation allocation,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Long, List<LocalDate>> datesByEvent,
            Map<Long, RecurringEventResponseDto> eventsById, Set<Long> candidateRoomIds,
            List<OccupiedSlot> databaseOccupancy, List<ResolvedProposal> resolvedProposals) {
        Long eventId = eventIdOf(allocation);
        AcademicEventResponseDto event = eventDtoById.get(eventId);
        List<LocalDate> dates = datesByEvent.getOrDefault(eventId, List.of());
        RecurringEventResponseDto recurringEvent = eventsById.get(eventId);
        List<MoveConflictDto> conflicts = recurringEvent == null ? List.of()
                : previewValidator.unresolvedConflicts(candidateRoomIds, Set.copyOf(dates),
                        recurringEvent.startTime(), recurringEvent.endTime(), databaseOccupancy, resolvedProposals);
        return new UnresolvedAllocationDto(event, dates, conflicts);
    }

    private static Long eventIdOf(OptimizerAllocation allocation) {
        return Long.valueOf(allocation.eventId());
    }
}
