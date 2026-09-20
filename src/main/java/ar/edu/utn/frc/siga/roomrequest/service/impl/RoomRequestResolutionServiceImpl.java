package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.TimeRanges;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestResolutionService;
import ar.edu.utn.frc.siga.roomrequest.validator.ItemConsistency;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestResolutionServiceImpl implements RoomRequestResolutionService {

    private static final String COMPUTERS_RESOURCE_NAME = "Cantidad de PC";
    private static final String PROJECTOR_RESOURCE_NAME = "Proyector";

    private final RoomRequestItemRepository itemRepository;
    private final RoomRequestTransitionValidator transitionValidator;
    private final AllocationService allocationService;
    private final AllocationOccupancyService allocationOccupancyService;
    private final ClassroomService classroomService;
    private final BuildingService buildingService;
    private final UserService userService;
    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final RoomRequestComposer composer;

    @Override
    @Transactional
    public RoomRequestItemResponseDto assign(Long itemId, List<Long> classroomIds, String reason, String actor) {
        log.debug("Asignando aula(s) a pedido: itemId={}, classroomIds={}", itemId, classroomIds);

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.PRE_APPROVED);

        if (item.getNotifiedAt() != null) {
            throw new InvalidRoomRequestException(
                    "El pedido ya fue notificado al docente; no se puede volver a asignar.");
        }

        List<Long> ids = classroomIds == null ? List.of() : classroomIds;
        if (ids.isEmpty()) {
            throw new InvalidRoomRequestException("Hay que asignar al menos un aula.");
        }
        if (ids.size() > item.getClassroomCount()) {
            throw new InvalidRoomRequestException("No se pueden asignar más aulas de las pedidas.");
        }
        ItemConsistency.requireDistinct(ids, "un aula");
        if (ids.size() < item.getClassroomCount() && (reason == null || reason.isBlank())) {
            throw new InvalidRoomRequestException(
                    "El motivo es obligatorio al asignar menos aulas de las pedidas.");
        }

        Long classroomId = ids.getFirst();
        boolean reassigning = !item.getAllocations().isEmpty();
        List<Long> occurrenceIds = resolveTargetOccurrences(item, reassigning);

        AllocationCommand command = AllocationCommand.manual(
                List.of(new AllocationItem(new AllocationTarget.Occurrences(occurrenceIds), classroomId)),
                "Pedido de aula #" + item.getId());
        if (createsEvent(item.getRequest().getType()) && !reassigning) {
            allocationService.allocate(command);
        } else {
            allocationService.reallocate(command);
        }

        item.assignClassroom(classroomId, occurrenceIds);
        item.decide(RoomRequestStatus.PRE_APPROVED, actor, reason, LocalDateTime.now());

        log.info("Pedido de aula asignado: itemId={}, classroomId={}, ocurrencias={}",
                itemId, classroomId, occurrenceIds.size());
        return composer.composeItem(item);
    }

    @Override
    @Transactional
    public RoomRequestItemResponseDto cancel(Long itemId, String reason, String actor) {
        log.debug("Cancelando pedido de aula: itemId={}", itemId);

        if (reason == null || reason.isBlank()) {
            throw new InvalidRoomRequestException("El motivo de cancelación es obligatorio.");
        }

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.CANCELLED);

        List<Long> occurrenceIds = item.getAllocations().stream()
                .map(RoomRequestItemAllocation::getOccurrenceId)
                .toList();
        if (!occurrenceIds.isEmpty()) {
            allocationService.deallocate(new DeallocationCommand(
                    List.of(new AllocationTarget.Occurrences(occurrenceIds)),
                    "Solicitud de aula cancelada: " + reason));
        }

        item.decide(RoomRequestStatus.CANCELLED, actor, reason, LocalDateTime.now());

        log.info("Pedido de aula cancelado: itemId={}, aulasLiberadas={}", itemId, occurrenceIds.size());
        return composer.composeItem(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllowedClassroomDto> findAllowedClassrooms(Long itemId) {
        log.debug("Buscando aulas candidatas para itemId={}", itemId);

        RoomRequestItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));

        Set<Long> occupiedClassroomIds = occupiedClassroomIds(item);
        return candidateClassrooms(item).stream()
                .map(classroom -> new AllowedClassroomDto(classroom.id(), classroom.roomNumber(),
                        classroom.buildingId(), classroom.buildingName(), classroom.capacity(),
                        !occupiedClassroomIds.contains(classroom.id())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateBuildingDto> findCandidateBuildings(Long itemId) {
        log.debug("Buscando edificios candidatos para itemId={}", itemId);

        RoomRequestItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));

        Set<Long> occupiedClassroomIds = occupiedClassroomIds(item);
        Map<Long, List<ClassroomResponseDto>> freeByBuilding = candidateClassrooms(item).stream()
                .filter(c -> !occupiedClassroomIds.contains(c.id()))
                .collect(Collectors.groupingBy(ClassroomResponseDto::buildingId, LinkedHashMap::new, Collectors.toList()));

        List<CandidateBuildingDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<ClassroomResponseDto>> entry : freeByBuilding.entrySet()) {
            Long buildingId = entry.getKey();
            if (userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, buildingId).isEmpty()) {
                continue;
            }
            result.add(new CandidateBuildingDto(buildingId, entry.getValue().getFirst().buildingName(),
                    entry.getValue().size()));
        }
        return result;
    }

    @Override
    @Transactional
    public RoomRequestItemResponseDto derive(Long itemId, Long buildingId, String actor) {
        log.debug("Derivando pedido de aula: itemId={}, buildingId={}", itemId, buildingId);

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.DERIVED_TO_BUILDING);

        BuildingResponseDto building = buildingService.findById(buildingId);
        if (!Boolean.TRUE.equals(building.active())) {
            throw new InvalidRoomRequestException("El edificio no está activo.");
        }
        if (userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, buildingId).isEmpty()) {
            throw new InvalidRoomRequestException("El edificio no tiene un auxiliar áulico asignado.");
        }

        Set<Long> occupiedClassroomIds = occupiedClassroomIds(item);
        boolean hasFreeClassroom = candidateClassrooms(item).stream()
                .anyMatch(c -> c.buildingId().equals(buildingId) && !occupiedClassroomIds.contains(c.id()));
        if (!hasFreeClassroom) {
            throw new InvalidRoomRequestException(
                    "El edificio no tiene ninguna aula libre que cumpla los requisitos del pedido.");
        }

        item.deriveTo(buildingId, actor, LocalDateTime.now());

        log.info("Pedido de aula derivado: itemId={}, buildingId={}", itemId, buildingId);
        return composer.composeItem(item);
    }

    @Override
    @Transactional
    public RoomRequestItemResponseDto returnItem(Long itemId, String reason, String actor) {
        log.debug("Devolviendo pedido de aula desde su edificio: itemId={}", itemId);

        if (reason == null || reason.isBlank()) {
            throw new InvalidRoomRequestException("El motivo de devolución es obligatorio.");
        }

        RoomRequestItem item = itemRepository.findWithRequestById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));
        transitionValidator.validateTransition(item.getStatus(), RoomRequestStatus.PENDING);

        item.returnFromBuilding(reason);

        log.info("Pedido de aula devuelto: itemId={}", itemId);
        return composer.composeItem(item);
    }

    private List<Long> resolveTargetOccurrences(RoomRequestItem item, boolean reassigning) {
        RoomRequestType type = item.getRequest().getType();
        return switch (type) {
            case ONE_TIME_ROOM_CHANGE, PARTIAL_EXAM_IN_CLASS -> List.of(findOccurrenceOnDate(item));
            case REGULAR_ROOM_CHANGE -> findFutureOccurrencesOnDayOfWeek(item);
            case PARTIAL_EXAM_OFF_SCHEDULE, FINAL_EXAM, CONFERENCE, OTHER -> reassigning
                    ? List.of(item.getAllocations().getFirst().getOccurrenceId())
                    : List.of(createEventOccurrence(item, type));
        };
    }

    private static boolean createsEvent(RoomRequestType type) {
        return switch (type) {
            case PARTIAL_EXAM_OFF_SCHEDULE, FINAL_EXAM, CONFERENCE, OTHER -> true;
            case ONE_TIME_ROOM_CHANGE, REGULAR_ROOM_CHANGE, PARTIAL_EXAM_IN_CLASS -> false;
        };
    }

    private Long findOccurrenceOnDate(RoomRequestItem item) {
        return occurrenceService.findSlotsByEvent(item.getSourceRecurringEventId(), null).stream()
                .filter(slot -> slot.date().equals(item.getDate()))
                .findFirst()
                .map(OccurrenceSlotDto::occurrenceId)
                .orElseThrow(() -> new InvalidRoomRequestException(
                        "No se encontró la clase de este pedido en el calendario."));
    }

    private List<Long> findFutureOccurrencesOnDayOfWeek(RoomRequestItem item) {
        List<Long> occurrenceIds = occurrenceService.findSlotsByEvent(item.getSourceRecurringEventId(), LocalDate.now())
                .stream()
                .filter(slot -> slot.date().getDayOfWeek() == item.getDayOfWeek())
                .map(OccurrenceSlotDto::occurrenceId)
                .toList();
        if (occurrenceIds.isEmpty()) {
            throw new InvalidRoomRequestException("No quedan clases futuras para este cambio regular de aula.");
        }
        return occurrenceIds;
    }

    private Long createEventOccurrence(RoomRequestItem item, RoomRequestType type) {
        UniqueEventKind kind = switch (type) {
            case PARTIAL_EXAM_OFF_SCHEDULE -> UniqueEventKind.PARCIAL;
            case FINAL_EXAM -> UniqueEventKind.EXAMEN_FINAL;
            default -> UniqueEventKind.OTRO;
        };
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(kind, item.getRequest().getSubjectId(),
                item.getCommissionId(), item.getDate(), item.getStartTime(),
                (int) item.getDuration().toMinutes(), item.getEstimated(), item.getObservations());
        AcademicEventResponseDto event = academicEventService.createUniqueEvent(dto);
        return academicEventService.findOccurrencesByEventId(event.id()).getFirst().id();
    }

    private List<ClassroomResponseDto> candidateClassrooms(RoomRequestItem item) {
        List<ClassroomResponseDto> candidates = classroomService.findAllAvailable();
        if (item.getRequiresComputers()) {
            Set<Long> withComputers = classroomService.findIdsWithResourceAtLeast(
                    COMPUTERS_RESOURCE_NAME, item.getComputerCount());
            candidates = candidates.stream().filter(c -> withComputers.contains(c.id())).toList();
        }
        if (item.getRequiresProjector()) {
            Set<Long> withProjector = classroomService.findIdsWithResourceAtLeast(PROJECTOR_RESOURCE_NAME, 1);
            candidates = candidates.stream().filter(c -> withProjector.contains(c.id())).toList();
        }
        return candidates;
    }

    private Set<Long> occupiedClassroomIds(RoomRequestItem item) {
        if (item.getDate() == null) {
            return Set.of();
        }
        return allocationOccupancyService.findOccupancy(item.getDate(), item.getDate()).stream()
                .filter(slot -> TimeRanges.overlaps(item.getStartTime(), item.endTime(),
                        slot.startTime(), slot.endTime()))
                .map(OccupiedSlot::classroomId)
                .collect(Collectors.toSet());
    }
}
