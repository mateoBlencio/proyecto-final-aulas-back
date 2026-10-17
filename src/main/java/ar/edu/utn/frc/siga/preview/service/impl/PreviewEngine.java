package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.optimizer.service.OptimizerService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.model.ClassroomResources;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
class PreviewEngine {

    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final ClassroomService classroomService;
    private final AllocationOccupancyService occupancyService;
    private final OptimizerService optimizerService;
    private final BuildingScopeResolver buildingScopeResolver;

    record Inputs(List<AcademicEventResponseDto> events, Map<PlanningKey, List<LocalDate>> datesByKey,
                  List<OptimizerRoom> rooms, List<OptimizerOccupancy> occupancy,
                  List<OccupiedSlot> databaseOccupancy, Map<PlanningKey, Long> priorRoomByKey,
                  Map<PlanningKey, List<OccupiedSlot>> priorSlotsByKey) {
    }

    record Suggestion(AcademicEventResponseDto event, ClassroomResponseDto classroom, int overcrowdedBy) {
    }

    @Transactional(readOnly = true)
    Inputs loadInputs(Set<Long> eventIds) {
        List<AcademicEventResponseDto> events = loadEvents(eventIds);
        Map<PlanningKey, List<LocalDate>> datesByKey = datesByKey(eventIds);
        List<ClassroomResponseDto> availableRooms = loadAvailableRooms(Permission.PREVIEW_RUN);
        Map<Long, ClassroomSubjectPermissionDto> permissionsByRoom = classroomService.findSubjectPermissions(
                availableRooms.stream().map(ClassroomResponseDto::id).toList());
        List<OptimizerRoom> rooms = toSolverRooms(availableRooms, permissionsByRoom,
                events.stream().anyMatch(PreviewEngine::requiresProjector),
                events.stream().anyMatch(PreviewEngine::requiresComputers));

        List<OccupiedSlot> occupancyInRange = loadOccupancyInRange(events);
        List<OccupiedSlot> databaseOccupancy = occupancyInRange.stream()
                .filter(o -> !eventIds.contains(o.eventId()))
                .toList();
        List<OccupiedSlot> ownOccupancy = occupancyInRange.stream()
                .filter(o -> eventIds.contains(o.eventId()))
                .toList();
        Map<PlanningKey, List<OccupiedSlot>> priorSlotsByKey = groupByKey(ownOccupancy);
        Map<PlanningKey, Long> priorRoomByKey = new HashMap<>();
        priorSlotsByKey.forEach((key, slots) -> priorRoomByKey.put(key, slots.getFirst().classroomId()));

        List<OptimizerOccupancy> occupancy = databaseOccupancy.stream().map(this::toOccupancy).toList();
        return new Inputs(events, datesByKey, rooms, occupancy, databaseOccupancy, priorRoomByKey,
                priorSlotsByKey);
    }

    OptimizationResult generate(Set<Long> eventIds, int timeLimitSeconds) {
        Inputs inputs = loadInputs(eventIds);
        Map<PlanningKey, Integer> divisors = PlanningKey.divisors(inputs.datesByKey());
        Set<Long> mirrored = PlanningKey.mirroredEventIds(inputs.datesByKey().keySet());
        List<OptimizerEvent> optimizerEvents = inputs.events().stream()
                .flatMap(e -> inputs.datesByKey().entrySet().stream()
                        .filter(entry -> entry.getKey().eventId().equals(e.id()))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> toSolverEvent(e, entry.getKey(), entry.getValue(),
                                divisors.get(entry.getKey()), mirrored.contains(e.id()))))
                .toList();
        if (optimizerEvents.isEmpty()) {
            throw new AllocationConflictException(
                    "los eventos indicados no tienen ocurrencias pendientes de asignación");
        }

        log.info("Auto-preview: {} eventos, {} aulas disponibles, {} franjas ocupadas",
                optimizerEvents.size(), inputs.rooms().size(), inputs.occupancy().size());

        return optimizerService.optimize(optimizerEvents, inputs.rooms(), inputs.occupancy(), timeLimitSeconds);
    }

    // Sin @Transactional: el solver no debe correr con una conexión tomada.
    Suggestion suggest(Long eventId, List<OccurrenceSlotDto> slots, Set<Long> excludedClassroomIds,
            int timeLimitSeconds) {
        List<AcademicEventResponseDto> found = academicEventService.findByIds(Set.of(eventId));
        if (found.isEmpty()) {
            throw ResourceNotFoundException.of("AcademicEvent", eventId);
        }
        AcademicEventResponseDto event = found.getFirst();

        List<ClassroomResponseDto> availableRooms = loadAvailableRooms(Permission.PREVIEW_RUN, Permission.ALLOCATION_WRITE)
                .stream()
                .filter(c -> !excludedClassroomIds.contains(c.id()))
                .toList();
        if (availableRooms.isEmpty()) {
            return new Suggestion(event, null, 0);
        }

        Map<Long, ClassroomSubjectPermissionDto> permissionsByRoom = classroomService.findSubjectPermissions(
                availableRooms.stream().map(ClassroomResponseDto::id).toList());
        List<OptimizerRoom> rooms = toSolverRooms(availableRooms, permissionsByRoom,
                requiresProjector(event), requiresComputers(event));

        OptimizerEvent optimizerEvent = toSuggestionEvent(eventId, event, slots);
        List<OptimizerOccupancy> occupancy = loadOccupancyForSuggestion(eventId, slots);

        log.info("Sugerencia de reasignación: evento={}, {} aulas candidatas, {} franjas ocupadas",
                eventId, rooms.size(), occupancy.size());

        OptimizationResult result = optimizerService.optimize(List.of(optimizerEvent), rooms, occupancy, timeLimitSeconds);
        OptimizerAllocation allocation = result.allocations().getFirst();
        if (allocation.classroomId() == null) {
            return new Suggestion(event, null, 0);
        }

        ClassroomResponseDto classroom = availableRooms.stream()
                .filter(c -> c.id().equals(allocation.classroomId()))
                .findFirst()
                .orElseThrow();
        OptimizerRoom solverRoom = rooms.stream()
                .filter(r -> r.id().equals(allocation.classroomId()))
                .findFirst()
                .orElseThrow();
        return new Suggestion(event, classroom, solverRoom.overcrowding(optimizerEvent.enrolled()));
    }

    private List<ClassroomResponseDto> loadAvailableRooms(Permission... permissions) {
        List<BuildingScope> scopes = Arrays.stream(permissions).map(buildingScopeResolver::scopeFor).toList();
        return classroomService.findAllAvailable().stream()
                .filter(c -> scopes.stream().allMatch(scope -> scope.allows(c.buildingId())))
                .toList();
    }

    private OptimizerEvent toSuggestionEvent(Long eventId, AcademicEventResponseDto event, List<OccurrenceSlotDto> slots) {
        String commissionKey = event instanceof RecurringEventResponseDto recurring && recurring.commission() != null
                ? String.valueOf(recurring.commission().id()) : null;
        Set<Long> subjectIds = event.subject() != null ? Set.of(event.subject().id()) : Set.of();
        Set<LocalDate> occurrenceDates = slots.stream().map(OccurrenceSlotDto::date).collect(Collectors.toSet());
        LocalTime startTime = slots.stream().map(OccurrenceSlotDto::startTime).min(Comparator.naturalOrder()).orElseThrow();
        LocalTime endTime = slots.stream().map(OccurrenceSlotDto::endTime).max(Comparator.naturalOrder()).orElseThrow();
        int enrolled = slots.stream()
                .map(OccurrenceSlotDto::enrolled)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(Objects.requireNonNullElse(event.enrolled(), 0));
        boolean needsProjector = requiresProjector(event);
        int computersNeeded = event instanceof UniqueEventResponseDto unique
                ? RoomRequirements.computersNeeded(unique, 1) : 0;
        return new OptimizerEvent(String.valueOf(eventId), commissionKey, enrolled, startTime, endTime,
                occurrenceDates, subjectIds, null, needsProjector, computersNeeded);
    }

    private List<OptimizerOccupancy> loadOccupancyForSuggestion(Long eventId, List<OccurrenceSlotDto> slots) {
        LocalDate minDate = slots.stream().map(OccurrenceSlotDto::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate maxDate = slots.stream().map(OccurrenceSlotDto::date).max(Comparator.naturalOrder()).orElseThrow();
        return occupancyService.findOccupancy(minDate, maxDate).stream()
                .filter(o -> !eventId.equals(o.eventId()))
                .map(this::toOccupancy)
                .toList();
    }

    private OptimizerEvent toSolverEvent(AcademicEventResponseDto e, PlanningKey key, List<LocalDate> dates,
            int divisor, boolean mirrored) {
        var commission = switch (e) {
            case RecurringEventResponseDto recurring -> recurring.commission();
            case UniqueEventResponseDto unique -> unique.commission();
        };
        String commissionKey = commission != null ? String.valueOf(commission.id()) : null;
        Set<Long> subjectIds = e.subject() != null ? Set.of(e.subject().id()) : Set.of();
        int enrolledPerRoom = Math.ceilDiv(Objects.requireNonNullElse(e.enrolled(), 0), divisor);
        String groupKey = mirrored ? String.valueOf(e.id()) : null;
        boolean needsProjector = requiresProjector(e);
        int computersNeeded = e instanceof UniqueEventResponseDto unique
                ? RoomRequirements.computersNeeded(unique, divisor) : 0;
        return new OptimizerEvent(key.toPlanningId(), commissionKey, enrolledPerRoom, e.startTime(),
                e.startTime().plusMinutes(e.durationMinutes()), Set.copyOf(dates), subjectIds, groupKey,
                needsProjector, computersNeeded);
    }

    private List<AcademicEventResponseDto> loadEvents(Set<Long> eventIds) {
        List<AcademicEventResponseDto> found = academicEventService.findByIds(eventIds);
        if (found.size() != eventIds.size()) {
            throw ResourceNotFoundException.of("AcademicEvent", eventIds);
        }
        return found;
    }

    private Map<PlanningKey, List<LocalDate>> datesByKey(Set<Long> eventIds) {
        return occurrenceService.findSlotsByEvents(eventIds, LocalDate.now())
                .stream()
                .filter(o -> o.status() == OccurrenceStatus.NEEDS_ROOM && !o.isPast())
                .collect(Collectors.groupingBy(
                        o -> new PlanningKey(o.eventId(), o.roomSlot()),
                        Collectors.mapping(OccurrenceSlotDto::date,
                                Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new),
                                        List::copyOf))));
    }

    private Map<PlanningKey, List<OccupiedSlot>> groupByKey(List<OccupiedSlot> ownOccupancy) {
        Map<Long, Integer> roomSlotByOccurrence = occurrenceService.findSlots(ownOccupancy.stream()
                        .map(OccupiedSlot::occurrenceId).filter(Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(OccurrenceSlotDto::occurrenceId, OccurrenceSlotDto::roomSlot));
        return ownOccupancy.stream().collect(Collectors.groupingBy(
                o -> new PlanningKey(o.eventId(), roomSlotByOccurrence.getOrDefault(o.occurrenceId(), 1))));
    }

    private static boolean requiresProjector(AcademicEventResponseDto e) {
        return e instanceof UniqueEventResponseDto unique && unique.requiresProjector();
    }

    private static boolean requiresComputers(AcademicEventResponseDto e) {
        return e instanceof UniqueEventResponseDto unique && RoomRequirements.computersNeeded(unique, 1) > 0;
    }

    private List<OptimizerRoom> toSolverRooms(List<ClassroomResponseDto> rooms,
            Map<Long, ClassroomSubjectPermissionDto> permissionsByRoom, boolean loadProjectors,
            boolean loadComputers) {
        List<Long> roomIds = rooms.stream().map(ClassroomResponseDto::id).toList();
        Map<Long, Integer> projectors = loadProjectors
                ? classroomService.findResourceAmounts(ClassroomResources.PROJECTOR, roomIds) : Map.of();
        Map<Long, Integer> computers = loadComputers
                ? classroomService.findResourceAmounts(ClassroomResources.COMPUTERS, roomIds) : Map.of();
        return rooms.stream()
                .map(c -> toSolverRoom(c, permissionsByRoom.get(c.id()),
                        projectors.getOrDefault(c.id(), 0) > 0, computers.getOrDefault(c.id(), 0)))
                .toList();
    }

    private OptimizerRoom toSolverRoom(ClassroomResponseDto c, ClassroomSubjectPermissionDto permission,
            boolean hasProjector, int computers) {
        boolean openToAll = permission == null || permission.openToAll();
        Set<Long> allowedSubjectIds = permission == null ? Set.of() : permission.allowedSubjectIds();
        return new OptimizerRoom(c.id(), c.capacity(), c.buildingId(), openToAll, allowedSubjectIds,
                hasProjector, computers);
    }

    private List<OccupiedSlot> loadOccupancyInRange(List<AcademicEventResponseDto> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        LocalDate from = events.stream().map(PreviewEngine::rangeStart)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = events.stream().map(PreviewEngine::rangeEnd)
                .max(Comparator.naturalOrder()).orElseThrow();

        return occupancyService.findOccupancy(from, to);
    }

    private static LocalDate rangeStart(AcademicEventResponseDto e) {
        return switch (e) {
            case RecurringEventResponseDto recurring -> recurring.startDate();
            case UniqueEventResponseDto unique -> unique.date();
        };
    }

    private static LocalDate rangeEnd(AcademicEventResponseDto e) {
        return switch (e) {
            case RecurringEventResponseDto recurring ->
                    recurring.endDate() != null ? recurring.endDate() : recurring.startDate().plusYears(1);
            case UniqueEventResponseDto unique -> unique.date();
        };
    }

    private OptimizerOccupancy toOccupancy(OccupiedSlot slot) {
        return new OptimizerOccupancy(slot.classroomId(), slot.date(), slot.startTime(), slot.endTime());
    }
}
