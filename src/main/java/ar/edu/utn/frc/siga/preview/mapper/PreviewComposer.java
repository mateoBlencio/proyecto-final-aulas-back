package ar.edu.utn.frc.siga.preview.mapper;

import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.Overcrowding;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewItemDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
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
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PreviewComposer {

    private final ClassroomService classroomService;
    private final PreviewValidator previewValidator;

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

        Set<Long> classroomIds = new LinkedHashSet<>(effectiveRoomByKey.values());
        priorSlotsByKey.values().stream().flatMap(List::stream).map(OccupiedSlot::classroomId).forEach(classroomIds::add);
        Map<Long, ClassroomResponseDto> classroomDtoById = Maps.byId(classroomService.findByIdsIncludingDeactivated(classroomIds), ClassroomResponseDto::id);

        Requirements requirements = loadRequirements(resolved, eventsById, effectiveRoomByKey);

        List<PreviewItemDto> allocations = resolved.stream()
                .map(key -> toPreviewItemDto(key, eventsById.get(key.eventId()), datesByKey,
                        classroomDtoById.get(effectiveRoomByKey.get(key)), priorRoomByKey,
                        roomStretches(priorSlotsByKey.get(key), classroomDtoById),
                        divisors.getOrDefault(key, 1), requirements))
                .toList();

        Set<Long> candidateRoomIds = rooms.stream().map(OptimizerRoom::id).collect(Collectors.toSet());
        List<ResolvedProposal> resolvedProposals = buildResolvedProposals(effectiveRoomByKey, eventsById, datesByKey);
        List<UnresolvedAllocationDto> unresolvedDtos = unresolved.stream()
                .map(key -> toUnresolvedAllocationDto(key, eventsById.get(key.eventId()), datesByKey,
                        candidateRoomIds, databaseOccupancy, resolvedProposals))
                .toList();

        return new PreviewResponseDto(preview.previewId(), allocations, unresolvedDtos);
    }

    private record Requirements(Map<Long, Integer> computersByRoom, Map<Long, Integer> projectorsByRoom) {
    }

    private Requirements loadRequirements(List<PlanningKey> resolved, Map<Long, AcademicEventResponseDto> eventsById,
            Map<PlanningKey, Long> effectiveRoomByKey) {
        List<UniqueEventResponseDto> uniques = resolved.stream()
                .map(key -> eventsById.get(key.eventId()))
                .filter(UniqueEventResponseDto.class::isInstance)
                .map(UniqueEventResponseDto.class::cast)
                .toList();
        Set<Long> roomIds = new LinkedHashSet<>(effectiveRoomByKey.values());
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

    private UnresolvedAllocationDto toUnresolvedAllocationDto(PlanningKey key, AcademicEventResponseDto event,
            Map<PlanningKey, List<LocalDate>> datesByKey, Set<Long> candidateRoomIds,
            List<OccupiedSlot> databaseOccupancy, List<ResolvedProposal> resolvedProposals) {
        List<LocalDate> dates = datesByKey.getOrDefault(key, List.of());
        List<MoveConflictDto> conflicts = event == null ? List.of()
                : previewValidator.unresolvedConflicts(candidateRoomIds, Set.copyOf(dates),
                        event.startTime(), endTime(event), databaseOccupancy, resolvedProposals);
        return new UnresolvedAllocationDto(event, key.roomSlot(), dates, conflicts);
    }

    private static LocalTime endTime(AcademicEventResponseDto event) {
        return event.startTime().plusMinutes(event.durationMinutes());
    }
}
