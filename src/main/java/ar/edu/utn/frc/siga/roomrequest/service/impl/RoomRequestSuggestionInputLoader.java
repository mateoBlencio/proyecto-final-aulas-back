package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.RoomRequestAlreadyNotifiedException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl.Action;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
class RoomRequestSuggestionInputLoader {

    record Inputs(Long itemId, Long itemVersion, int classroomCount, int enrolled, LocalTime startTime,
                  LocalTime endTime, Set<LocalDate> dates, Set<Long> subjectIds,
                  List<ClassroomResponseDto> classrooms, List<OptimizerRoom> rooms,
                  List<OptimizerOccupancy> occupancy) {
    }

    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestTransitionValidator transitionValidator;
    private final RoomRequestAccessControl accessControl;
    private final RoomRequestCandidateResolver candidateResolver;
    private final RoomRequestOccurrenceResolver occurrenceResolver;
    private final ClassroomService classroomService;
    private final AllocationOccupancyService allocationOccupancyService;
    private final AcademicEventService academicEventService;

    @Transactional(readOnly = true)
    Inputs load(Long itemId, Set<Long> excludedClassroomIds, String actor) {
        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        if (item.getStatus() == RoomRequestStatus.RESOLVED) {
            throw new RoomRequestAlreadyNotifiedException(itemId);
        }
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.IN_EVALUATION);
        accessControl.authorize(item, actor, Action.ASSIGN);

        if (item.getStartTime() == null || item.endTime() == null) {
            throw new InvalidRoomRequestException("El pedido no tiene horario para sugerir un aula.");
        }
        Set<LocalDate> dates = datesOf(item);
        int enrolled = enrolledOf(item);

        Set<Long> ownOccurrenceIds = item.getAllocations().stream()
                .map(RoomRequestItemAllocation::getOccurrenceId)
                .collect(Collectors.toSet());
        LocalDate from = dates.stream().min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = dates.stream().max(Comparator.naturalOrder()).orElseThrow();
        Long sourceEventId = item.getSourceRecurringEventId();
        List<OccupiedSlot> slots = allocationOccupancyService.findOccupancy(from, to).stream()
                .filter(slot -> !ownOccurrenceIds.contains(slot.occurrenceId()))
                .toList();
        Set<Long> currentClassroomIds = sourceEventId == null ? Set.of() : slots.stream()
                .filter(slot -> sourceEventId.equals(slot.eventId()))
                .map(OccupiedSlot::classroomId)
                .collect(Collectors.toSet());

        List<ClassroomResponseDto> classrooms = candidateResolver.candidateClassrooms(item).stream()
                .filter(c -> !excludedClassroomIds.contains(c.id()))
                .filter(c -> !currentClassroomIds.contains(c.id()))
                .filter(c -> item.getDerivedBuildingId() == null || item.getDerivedBuildingId().equals(c.buildingId()))
                .toList();
        Map<Long, ClassroomSubjectPermissionDto> permissionsByRoom = classrooms.isEmpty() ? Map.of()
                : classroomService.findSubjectPermissions(classrooms.stream().map(ClassroomResponseDto::id).toList());
        List<OptimizerRoom> rooms = classrooms.stream().map(c -> toSolverRoom(c, permissionsByRoom.get(c.id()))).toList();

        List<OptimizerOccupancy> occupancy = slots.stream()
                .filter(slot -> sourceEventId == null || !sourceEventId.equals(slot.eventId()))
                .map(this::toOccupancy)
                .toList();

        Set<Long> subjectIds = item.getRequest().getSubjectId() != null
                ? Set.of(item.getRequest().getSubjectId()) : Set.of();
        return new Inputs(itemId, item.getVersion(), item.getClassroomCount(), enrolled, item.getStartTime(),
                item.endTime(), dates, subjectIds, classrooms, rooms, occupancy);
    }

    private Set<LocalDate> datesOf(RoomRequestItem item) {
        if (item.getRequest().getType() == RoomRequestType.REGULAR_ROOM_CHANGE) {
            return Set.copyOf(occurrenceResolver.futureDatesOnDayOfWeek(item));
        }
        if (item.getDate() == null) {
            throw new InvalidRoomRequestException("El pedido no tiene fecha para sugerir un aula.");
        }
        return Set.of(item.getDate());
    }

    // Los cambios de aula no piden cantidad: se toma la del evento recurrente de la comisión.
    private int enrolledOf(RoomRequestItem item) {
        if (item.getEstimated() != null) {
            return item.getEstimated();
        }
        Long sourceEventId = item.getSourceRecurringEventId();
        return (sourceEventId == null ? Stream.<AcademicEventResponseDto>empty()
                : academicEventService.findByIds(Set.of(sourceEventId)).stream())
                .map(AcademicEventResponseDto::enrolled)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new InvalidRoomRequestException(
                        "El pedido no tiene cantidad de inscriptos para sugerir un aula."));
    }

    private OptimizerRoom toSolverRoom(ClassroomResponseDto c, ClassroomSubjectPermissionDto permission) {
        boolean openToAll = permission == null || permission.openToAll();
        Set<Long> allowedSubjectIds = permission == null ? Set.of() : permission.allowedSubjectIds();
        return new OptimizerRoom(c.id(), c.capacity(), c.buildingId(), openToAll, allowedSubjectIds);
    }

    private OptimizerOccupancy toOccupancy(OccupiedSlot slot) {
        return new OptimizerOccupancy(slot.classroomId(), slot.date(), slot.startTime(), slot.endTime());
    }
}
