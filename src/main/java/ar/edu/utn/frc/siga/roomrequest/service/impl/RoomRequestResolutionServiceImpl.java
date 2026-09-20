package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.TimeRanges;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestResolutionService;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserService userService;
    private final RoomRequestComposer composer;

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
