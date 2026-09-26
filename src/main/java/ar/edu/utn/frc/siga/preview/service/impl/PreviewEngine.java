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
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
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
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
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

    record Inputs(List<RecurringEventResponseDto> events, Map<Long, List<LocalDate>> datesByEvent,
                  List<OptimizerRoom> rooms, List<OptimizerOccupancy> occupancy,
                  List<OccupiedSlot> databaseOccupancy, Map<Long, Long> priorRoomByEvent,
                  Map<Long, List<OccupiedSlot>> priorSlotsByEvent) {
    }

    record Suggestion(AcademicEventResponseDto event, ClassroomResponseDto classroom, int overcrowdedBy) {
    }

    @Transactional(readOnly = true)
    Inputs loadInputs(Set<Long> eventIds) {
        List<RecurringEventResponseDto> events = loadRecurringEvents(eventIds);
        Map<Long, List<LocalDate>> datesByEvent = datesByEvent(eventIds);
        List<ClassroomResponseDto> availableRooms = loadAvailableRooms(Permission.PREVIEW_RUN);
        Map<Long, ClassroomSubjectPermissionDto> permissionsByRoom = classroomService.findSubjectPermissions(
                availableRooms.stream().map(ClassroomResponseDto::id).toList());
        List<OptimizerRoom> rooms = availableRooms.stream()
                .map(c -> toSolverRoom(c, permissionsByRoom.get(c.id())))
                .toList();

        List<OccupiedSlot> occupancyInRange = loadOccupancyInRange(events);
        List<OccupiedSlot> databaseOccupancy = occupancyInRange.stream()
                .filter(o -> !eventIds.contains(o.eventId()))
                .toList();
        List<OccupiedSlot> ownOccupancy = occupancyInRange.stream()
                .filter(o -> eventIds.contains(o.eventId()))
                .toList();
        Map<Long, Long> priorRoomByEvent = ownOccupancy.stream()
                .collect(Collectors.toMap(OccupiedSlot::eventId, OccupiedSlot::classroomId, (x, y) -> x));
        Map<Long, List<OccupiedSlot>> priorSlotsByEvent = ownOccupancy.stream()
                .collect(Collectors.groupingBy(OccupiedSlot::eventId));

        List<OptimizerOccupancy> occupancy = databaseOccupancy.stream().map(this::toOccupancy).toList();
        return new Inputs(events, datesByEvent, rooms, occupancy, databaseOccupancy, priorRoomByEvent,
                priorSlotsByEvent);
    }

    OptimizationResult generate(Set<Long> eventIds, int timeLimitSeconds) {
        Inputs inputs = loadInputs(eventIds);
        List<OptimizerEvent> optimizerEvents = inputs.events().stream()
                .map(e -> toSolverEvent(e, inputs.datesByEvent().getOrDefault(e.id(), List.of())))
                .filter(e -> !e.occurrenceDates().isEmpty())
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
        List<OptimizerRoom> rooms = availableRooms.stream()
                .map(c -> toSolverRoom(c, permissionsByRoom.get(c.id())))
                .toList();

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
        return new OptimizerEvent(String.valueOf(eventId), commissionKey, enrolled, startTime, endTime,
                occurrenceDates, subjectIds);
    }

    private List<OptimizerOccupancy> loadOccupancyForSuggestion(Long eventId, List<OccurrenceSlotDto> slots) {
        LocalDate minDate = slots.stream().map(OccurrenceSlotDto::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate maxDate = slots.stream().map(OccurrenceSlotDto::date).max(Comparator.naturalOrder()).orElseThrow();
        return occupancyService.findOccupancy(minDate, maxDate).stream()
                .filter(o -> !eventId.equals(o.eventId()))
                .map(this::toOccupancy)
                .toList();
    }

    private OptimizerEvent toSolverEvent(RecurringEventResponseDto e, List<LocalDate> dates) {
        String commissionKey = e.commission() != null ? String.valueOf(e.commission().id()) : null;
        Set<Long> subjectIds = e.subject() != null ? Set.of(e.subject().id()) : Set.of();
        return new OptimizerEvent(String.valueOf(e.id()), commissionKey, e.enrolled(),
                e.startTime(), e.endTime(), Set.copyOf(dates), subjectIds);
    }

    private List<RecurringEventResponseDto> loadRecurringEvents(Set<Long> eventIds) {
        List<AcademicEventResponseDto> found = academicEventService.findByIds(eventIds);
        if (found.size() != eventIds.size()) {
            throw ResourceNotFoundException.of("AcademicEvent", eventIds);
        }
        return found.stream().map(e -> {
            if (!(e instanceof RecurringEventResponseDto recurring)) {
                throw new AllocationConflictException(
                        "auto-preview solo soporta eventos recurrentes por ahora (evento " + e.id() + ")");
            }
            return recurring;
        }).toList();
    }

    private Map<Long, List<LocalDate>> datesByEvent(Set<Long> eventIds) {
        return occurrenceService.findSlotsByEvents(eventIds, LocalDate.now())
                .stream()
                .collect(Collectors.groupingBy(
                        OccurrenceSlotDto::eventId,
                        Collectors.mapping(OccurrenceSlotDto::date,
                                Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new),
                                        List::copyOf))));
    }

    private OptimizerRoom toSolverRoom(ClassroomResponseDto c, ClassroomSubjectPermissionDto permission) {
        boolean openToAll = permission == null || permission.openToAll();
        Set<Long> allowedSubjectIds = permission == null ? Set.of() : permission.allowedSubjectIds();
        return new OptimizerRoom(c.id(), c.capacity(), c.buildingId(), openToAll, allowedSubjectIds);
    }

    private List<OccupiedSlot> loadOccupancyInRange(List<RecurringEventResponseDto> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        LocalDate from = events.stream().map(RecurringEventResponseDto::startDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = events.stream()
                .map(e -> e.endDate() != null ? e.endDate() : e.startDate().plusYears(1))
                .max(Comparator.naturalOrder()).orElseThrow();

        return occupancyService.findOccupancy(from, to);
    }

    private OptimizerOccupancy toOccupancy(OccupiedSlot slot) {
        return new OptimizerOccupancy(slot.classroomId(), slot.date(), slot.startTime(), slot.endTime());
    }
}
