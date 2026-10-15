package ar.edu.utn.frc.siga.preview.mapper;

import ar.edu.utn.frc.siga.allocation.config.AllocationSettings;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.Overcrowding;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewItemDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.RoomAlternativeDto;
import ar.edu.utn.frc.siga.preview.dto.response.RoomAlternativeDto.AlternativeReason;
import ar.edu.utn.frc.siga.preview.dto.response.RoomStretchDto;
import ar.edu.utn.frc.siga.preview.dto.response.UnmetRequirementDto;
import ar.edu.utn.frc.siga.preview.dto.response.UnmetRequirementDto.RequirementSeverity;
import ar.edu.utn.frc.siga.preview.dto.response.UnmetRequirementDto.RequirementType;
import ar.edu.utn.frc.siga.preview.service.impl.PlanningKey;
import ar.edu.utn.frc.siga.preview.service.impl.RoomRequirements;
import ar.edu.utn.frc.siga.preview.dto.response.UnresolvedAllocationDto;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator.ResolvedProposal;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomResources;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class PreviewComposer {

    private static final int MAX_ALTERNATIVES = 5;

    private final ClassroomService classroomService;
    private final PreviewValidator previewValidator;
    private final AllocationSettings allocationSettings;

    public PreviewResponseDto compose(OptimizationResult preview, List<AcademicEventResponseDto> events,
                                            Map<PlanningKey, List<LocalDate>> datesByKey,
                                            Map<PlanningKey, Long> priorRoomByKey,
                                            Map<PlanningKey, List<OccupiedSlot>> priorSlotsByKey,
                                            List<OptimizerRoom> rooms, List<OccupiedSlot> databaseOccupancy) {
        Map<Long, AcademicEventResponseDto> eventsById = Maps.byId(events, AcademicEventResponseDto::id);
        Map<PlanningKey, Integer> divisors = PlanningKey.divisors(datesByKey);

        List<PlanningKey> resolved = new ArrayList<>();
        List<PlanningKey> unresolved = new ArrayList<>();
        Map<PlanningKey, Long> effectiveRoomByKey = new LinkedHashMap<>();
        for (OptimizerAllocation allocation : preview.allocations()) {
            PlanningKey key = PlanningKey.parse(allocation.eventId());
            Long classroomId = allocation.classroomId();
            if (classroomId == null) {
                classroomId = priorRoomByKey.get(key);
            }
            if (classroomId != null) {
                resolved.add(key);
                effectiveRoomByKey.put(key, classroomId);
            } else {
                unresolved.add(key);
            }
        }

        Map<Long, OptimizerRoom> roomsById = Maps.byId(rooms, OptimizerRoom::id);
        List<ResolvedProposal> resolvedProposals = buildResolvedProposals(effectiveRoomByKey, eventsById, datesByKey);
        Map<PlanningKey, List<AlternativeCandidate>> alternativesByKey = new LinkedHashMap<>();
        for (PlanningKey key : unresolved) {
            alternativesByKey.put(key, alternativeCandidates(key, eventsById.get(key.eventId()), datesByKey,
                    roomsById, divisors.getOrDefault(key, 1), databaseOccupancy, resolvedProposals));
        }

        Set<Long> classroomIds = new LinkedHashSet<>(effectiveRoomByKey.values());
        priorSlotsByKey.values().stream().flatMap(List::stream).map(OccupiedSlot::classroomId).forEach(classroomIds::add);
        alternativesByKey.values().stream().flatMap(List::stream).map(AlternativeCandidate::classroomId)
                .forEach(classroomIds::add);
        Map<Long, ClassroomResponseDto> classroomDtoById = Maps.byId(classroomService.findByIdsIncludingDeactivated(classroomIds), ClassroomResponseDto::id);

        List<AcademicEventResponseDto> relevantEvents = Stream.concat(resolved.stream(), unresolved.stream())
                .map(key -> eventsById.get(key.eventId()))
                .toList();
        Requirements requirements = loadRequirements(relevantEvents, classroomIds);

        List<PreviewItemDto> allocations = resolved.stream()
                .map(key -> toPreviewItemDto(key, eventsById.get(key.eventId()), datesByKey,
                        classroomDtoById.get(effectiveRoomByKey.get(key)), priorRoomByKey,
                        roomStretches(priorSlotsByKey.get(key), classroomDtoById),
                        divisors.getOrDefault(key, 1), requirements))
                .toList();

        Set<Long> candidateRoomIds = roomsById.keySet();
        List<UnresolvedAllocationDto> unresolvedDtos = unresolved.stream()
                .map(key -> toUnresolvedAllocationDto(key, eventsById.get(key.eventId()), datesByKey,
                        candidateRoomIds, databaseOccupancy, resolvedProposals,
                        alternativesByKey.get(key), classroomDtoById, divisors.getOrDefault(key, 1), requirements))
                .toList();

        return new PreviewResponseDto(preview.previewId(), allocations, unresolvedDtos);
    }

    private record Requirements(Map<Long, Integer> computersByRoom, Map<Long, Integer> projectorsByRoom) {
    }

    private Requirements loadRequirements(List<AcademicEventResponseDto> events, Set<Long> roomIds) {
        List<UniqueEventResponseDto> uniques = events.stream()
                .filter(UniqueEventResponseDto.class::isInstance)
                .map(UniqueEventResponseDto.class::cast)
                .toList();
        Map<Long, Integer> computers = uniques.stream().anyMatch(u -> u.requiresComputers() && u.computerCount() != null)
                ? classroomService.findResourceAmounts(ClassroomResources.COMPUTERS, roomIds) : Map.of();
        Map<Long, Integer> projectors = uniques.stream().anyMatch(UniqueEventResponseDto::requiresProjector)
                ? classroomService.findResourceAmounts(ClassroomResources.PROJECTOR, roomIds) : Map.of();
        return new Requirements(computers, projectors);
    }

    private static List<UnmetRequirementDto> unmetRequirements(AcademicEventResponseDto event, ClassroomResponseDto classroom,
            int divisor, Requirements requirements) {
        if (!(event instanceof UniqueEventResponseDto unique) || classroom == null) {
            return List.of();
        }
        List<UnmetRequirementDto> unmet = new ArrayList<>();
        if (unique.requiresProjector() && requirements.projectorsByRoom().getOrDefault(classroom.id(), 0) <= 0) {
            unmet.add(new UnmetRequirementDto(RequirementType.PROJECTOR, RequirementSeverity.MINOR));
        }
        if (unique.requiresComputers() && unique.computerCount() != null
                && requirements.computersByRoom().getOrDefault(classroom.id(), 0)
                        < RoomRequirements.computersNeeded(unique, divisor)) {
            unmet.add(new UnmetRequirementDto(RequirementType.COMPUTERS, RequirementSeverity.SEVERE));
        }
        if (unique.requiredSoftware() != null && !unique.requiredSoftware().isBlank()) {
            unmet.add(new UnmetRequirementDto(RequirementType.SOFTWARE, RequirementSeverity.UNVERIFIABLE));
        }
        if (Boolean.TRUE.equals(unique.requiresExamUsers())) {
            unmet.add(new UnmetRequirementDto(RequirementType.EXAM_USERS, RequirementSeverity.UNVERIFIABLE));
        }
        return unmet;
    }

    private List<ResolvedProposal> buildResolvedProposals(Map<PlanningKey, Long> effectiveRoomByKey,
            Map<Long, AcademicEventResponseDto> eventsById, Map<PlanningKey, List<LocalDate>> datesByKey) {
        List<ResolvedProposal> proposals = new ArrayList<>();
        for (Map.Entry<PlanningKey, Long> entry : effectiveRoomByKey.entrySet()) {
            AcademicEventResponseDto event = eventsById.get(entry.getKey().eventId());
            if (event == null) continue;
            proposals.add(new ResolvedProposal(entry.getKey().eventId(), entry.getValue(),
                    datesByKey.getOrDefault(entry.getKey(), List.of()), event.startTime(), endTime(event)));
        }
        return proposals;
    }

    private PreviewItemDto toPreviewItemDto(PlanningKey key, AcademicEventResponseDto event,
            Map<PlanningKey, List<LocalDate>> datesByKey, ClassroomResponseDto classroom,
            Map<PlanningKey, Long> priorRoomByKey, List<RoomStretchDto> currentRoomStretches,
            int divisor, Requirements requirements) {
        boolean unchanged = classroom != null && Objects.equals(classroom.id(), priorRoomByKey.get(key));
        Integer enrolledPerRoom = event != null && event.enrolled() != null
                ? Math.ceilDiv(event.enrolled(), divisor) : null;
        return new PreviewItemDto(
                event, key.roomSlot(), datesByKey.getOrDefault(key, List.of()), classroom,
                Objects.requireNonNullElse(Overcrowding.by(
                        enrolledPerRoom, classroom != null ? classroom.capacity() : null), 0),
                unchanged, currentRoomStretches, unmetRequirements(event, classroom, divisor, requirements));
    }

    // Colapsa la ocupación de un evento en tramos continuos de misma aula, y devuelve la lista
    // solo si hay más de uno: con un solo tramo no hay nada que avisar, y devolverlo obligaría
    // al front a comparar para saber si mostrar algo.
    private static List<RoomStretchDto> roomStretches(
            List<OccupiedSlot> slots, Map<Long, ClassroomResponseDto> classroomDtoById) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        List<OccupiedSlot> ordered = slots.stream().sorted(Comparator.comparing(OccupiedSlot::date)).toList();

        List<RoomStretchDto> stretches = new ArrayList<>();
        Long classroomId = ordered.getFirst().classroomId();
        LocalDate from = ordered.getFirst().date();
        LocalDate to = from;
        int classes = 0;
        for (OccupiedSlot slot : ordered) {
            if (!Objects.equals(slot.classroomId(), classroomId)) {
                stretches.add(new RoomStretchDto(classroomDtoById.get(classroomId), from, to, classes));
                classroomId = slot.classroomId();
                from = slot.date();
                classes = 0;
            }
            to = slot.date();
            classes++;
        }
        stretches.add(new RoomStretchDto(classroomDtoById.get(classroomId), from, to, classes));

        return stretches.size() > 1 ? List.copyOf(stretches) : List.of();
    }

    private record AlternativeCandidate(Long classroomId, AlternativeReason reason, int overlapMinutes,
                                         Long conflictingEventId, int overcrowdedBy) {
    }

    // Solo ítems de una fecha: con varias, el solape por aula es una lista de fechas y no hay una cifra para ordenar.
    private List<AlternativeCandidate> alternativeCandidates(PlanningKey key, AcademicEventResponseDto event,
            Map<PlanningKey, List<LocalDate>> datesByKey, Map<Long, OptimizerRoom> roomsById, int divisor,
            List<OccupiedSlot> databaseOccupancy, List<ResolvedProposal> resolvedProposals) {
        List<LocalDate> dates = datesByKey.getOrDefault(key, List.of());
        if (event == null || dates.size() != 1) {
            return List.of();
        }
        int enrolledPerRoom = Math.ceilDiv(Objects.requireNonNullElse(event.enrolled(), 0), divisor);
        Set<Long> subjectIds = event.subject() != null ? Set.of(event.subject().id()) : Set.of();
        return previewValidator.roomOverlaps(roomsById.keySet(), dates.getFirst(), event.startTime(), endTime(event),
                        databaseOccupancy, resolvedProposals, allocationSettings.getMaxOverlapMinutes()).stream()
                .map(overlap -> toAlternativeCandidate(overlap, roomsById.get(overlap.classroomId()),
                        subjectIds, enrolledPerRoom))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(AlternativeCandidate::reason)
                        .thenComparingInt(AlternativeCandidate::overlapMinutes)
                        .thenComparingInt(AlternativeCandidate::overcrowdedBy))
                .limit(MAX_ALTERNATIVES)
                .toList();
    }

    private static Optional<AlternativeCandidate> toAlternativeCandidate(PreviewValidator.RoomOverlap overlap,
            OptimizerRoom room, Set<Long> subjectIds, int enrolledPerRoom) {
        int overcrowdedBy = room.overcrowding(enrolledPerRoom);
        if (overlap.overlapMinutes() > 0) {
            return Optional.of(new AlternativeCandidate(room.id(), AlternativeReason.OVERLAP,
                    overlap.overlapMinutes(), overlap.conflictingEventId(), overcrowdedBy));
        }
        // Aula libre que el solver no usó: solo se explica por el permiso de materia.
        return room.permits(subjectIds) ? Optional.empty()
                : Optional.of(new AlternativeCandidate(room.id(), AlternativeReason.SUBJECT_NOT_PERMITTED,
                        0, null, overcrowdedBy));
    }

    private UnresolvedAllocationDto toUnresolvedAllocationDto(PlanningKey key, AcademicEventResponseDto event,
            Map<PlanningKey, List<LocalDate>> datesByKey, Set<Long> candidateRoomIds,
            List<OccupiedSlot> databaseOccupancy, List<ResolvedProposal> resolvedProposals,
            List<AlternativeCandidate> candidates, Map<Long, ClassroomResponseDto> classroomDtoById,
            int divisor, Requirements requirements) {
        List<LocalDate> dates = datesByKey.getOrDefault(key, List.of());
        List<MoveConflictDto> conflicts = event == null ? List.of()
                : previewValidator.unresolvedConflicts(candidateRoomIds, Set.copyOf(dates),
                        event.startTime(), endTime(event), databaseOccupancy, resolvedProposals);
        List<RoomAlternativeDto> alternatives = candidates.stream()
                .map(c -> {
                    ClassroomResponseDto classroom = classroomDtoById.get(c.classroomId());
                    return new RoomAlternativeDto(classroom, c.reason(), c.overlapMinutes(), c.conflictingEventId(),
                            c.overcrowdedBy(), unmetRequirements(event, classroom, divisor, requirements));
                })
                .toList();
        return new UnresolvedAllocationDto(event, key.roomSlot(), dates, conflicts, alternatives);
    }

    private static LocalTime endTime(AcademicEventResponseDto event) {
        return event.startTime().plusMinutes(event.durationMinutes());
    }
}
