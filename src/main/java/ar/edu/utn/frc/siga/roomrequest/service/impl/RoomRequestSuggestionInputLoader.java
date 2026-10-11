package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
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
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class RoomRequestSuggestionInputLoader {

    record Inputs(Long itemId, Long itemVersion, int classroomCount, int enrolled, LocalTime startTime,
                  LocalTime endTime, Set<LocalDate> dates, Set<Long> subjectIds,
                  List<ClassroomResponseDto> classrooms, List<OptimizerRoom> rooms,
                  List<OptimizerOccupancy> occupancy, Integer requiredComputers,
                  Map<Long, Integer> computersByClassroom) {
    }

    private record LabCombo(List<ClassroomResponseDto> labs, int needed, boolean reachable, int totalPcs) {
    }

    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestTransitionValidator transitionValidator;
    private final RoomRequestAccessControl accessControl;
    private final RoomRequestCandidateResolver candidateResolver;
    private final RoomRequestOccurrenceResolver occurrenceResolver;
    private final ClassroomService classroomService;
    private final AllocationOccupancyService allocationOccupancyService;
    private final RoomRequestAttendees attendees;

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
        boolean lab = Boolean.TRUE.equals(item.getRequiresComputers());
        int enrolled = lab ? item.getComputerCount() : enrolledOf(item);

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
        Map<Long, Integer> computersByClassroom = Map.of();
        int classroomCount = item.getClassroomCount();
        if (lab) {
            computersByClassroom = candidateResolver.computersByClassroom(
                    classrooms.stream().map(ClassroomResponseDto::id).toList());
            LabCombo combo = bestCombo(classrooms, computersByClassroom, enrolled);
            classrooms = combo == null ? List.of() : combo.labs();
            classroomCount = combo == null ? 1 : combo.needed();
        }
        Map<Long, ClassroomSubjectPermissionDto> permissionsByRoom = classrooms.isEmpty() ? Map.of()
                : classroomService.findSubjectPermissions(classrooms.stream().map(ClassroomResponseDto::id).toList());
        Map<Long, Integer> pcs = computersByClassroom;
        List<OptimizerRoom> rooms = classrooms.stream()
                .map(c -> toSolverRoom(c, permissionsByRoom.get(c.id()), lab ? pcs.getOrDefault(c.id(), 0) : c.capacity()))
                .toList();

        List<OptimizerOccupancy> occupancy = slots.stream()
                .filter(slot -> sourceEventId == null || !sourceEventId.equals(slot.eventId()))
                .map(this::toOccupancy)
                .toList();

        Set<Long> subjectIds = item.getRequest().getSubjectId() != null
                ? Set.of(item.getRequest().getSubjectId()) : Set.of();
        return new Inputs(itemId, item.getVersion(), classroomCount, enrolled, item.getStartTime(),
                item.endTime(), dates, subjectIds, classrooms, rooms, occupancy,
                lab ? enrolled : null, computersByClassroom);
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
        return attendees.of(item).orElseThrow(() -> new InvalidRoomRequestException(
                "El pedido no tiene cantidad de inscriptos para sugerir un aula."));
    }

    // Elige un solo edificio (las aulas de un pedido van juntas): el que cubre las PCs con menos laboratorios y, a
    // igualdad, el que más PCs suma. Si ninguno llega, el que más PCs suma, con todos sus laboratorios.
    private LabCombo bestCombo(List<ClassroomResponseDto> labs, Map<Long, Integer> pcs, int required) {
        return labs.stream()
                .collect(Collectors.groupingBy(ClassroomResponseDto::buildingId)).values().stream()
                .map(building -> comboOf(building, pcs, required))
                .min(Comparator.comparing((LabCombo c) -> !c.reachable())
                        .thenComparing(c -> c.reachable() ? c.needed() : 0)
                        .thenComparing(LabCombo::totalPcs, Comparator.reverseOrder()))
                .orElse(null);
    }

    private LabCombo comboOf(List<ClassroomResponseDto> building, Map<Long, Integer> pcs, int required) {
        List<ClassroomResponseDto> sorted = building.stream()
                .sorted(Comparator.comparingInt((ClassroomResponseDto c) -> pcs.getOrDefault(c.id(), 0)).reversed())
                .toList();
        int total = sorted.stream().mapToInt(c -> pcs.getOrDefault(c.id(), 0)).sum();
        int sum = 0;
        for (int i = 0; i < sorted.size(); i++) {
            sum += pcs.getOrDefault(sorted.get(i).id(), 0);
            if (sum >= required) {
                return new LabCombo(sorted, i + 1, true, total);
            }
        }
        return new LabCombo(sorted, sorted.size(), false, total);
    }

    private OptimizerRoom toSolverRoom(ClassroomResponseDto c, ClassroomSubjectPermissionDto permission, int capacity) {
        boolean openToAll = permission == null || permission.openToAll();
        Set<Long> allowedSubjectIds = permission == null ? Set.of() : permission.allowedSubjectIds();
        return new OptimizerRoom(c.id(), capacity, c.buildingId(), openToAll, allowedSubjectIds);
    }

    private OptimizerOccupancy toOccupancy(OccupiedSlot slot) {
        return new OptimizerOccupancy(slot.classroomId(), slot.date(), slot.startTime(), slot.endTime());
    }
}
