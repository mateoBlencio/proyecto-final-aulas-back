package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.TimeRanges;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
class RoomRequestCandidateResolver {

    private static final String COMPUTERS_RESOURCE_NAME = "Cantidad de PC";
    private static final String PROJECTOR_RESOURCE_NAME = "Proyector";

    private final RoomRequestItemRepository itemRepository;
    private final ClassroomService classroomService;
    private final AllocationOccupancyService allocationOccupancyService;
    private final UserService userService;
    private final RoomRequestOccurrenceResolver occurrenceResolver;

    @Transactional(readOnly = true)
    List<AllowedClassroomDto> findAllowedClassrooms(Long itemId) {
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

    @Transactional(readOnly = true)
    List<CandidateBuildingDto> findCandidateBuildings(Long itemId) {
        log.debug("Buscando edificios candidatos para itemId={}", itemId);

        RoomRequestItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));

        Set<Long> occupiedClassroomIds = occupiedClassroomIds(item);
        Map<Long, List<ClassroomResponseDto>> freeByBuilding = candidateClassrooms(item).stream()
                .filter(c -> !occupiedClassroomIds.contains(c.id()))
                .collect(Collectors.groupingBy(ClassroomResponseDto::buildingId, LinkedHashMap::new, Collectors.toList()));
        if (freeByBuilding.isEmpty()) {
            return List.of();
        }

        Set<Long> buildingIdsWithAuxiliar = userService.findBuildingIdsCoveredByRole(
                SystemRole.AUXILIAR_AULICO, freeByBuilding.keySet());

        List<CandidateBuildingDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<ClassroomResponseDto>> entry : freeByBuilding.entrySet()) {
            Long buildingId = entry.getKey();
            if (!buildingIdsWithAuxiliar.contains(buildingId)) {
                continue;
            }
            result.add(new CandidateBuildingDto(buildingId, entry.getValue().getFirst().buildingName(),
                    entry.getValue().size()));
        }
        return result;
    }

    List<ClassroomResponseDto> candidateClassrooms(RoomRequestItem item) {
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

    Set<Long> occupiedClassroomIds(RoomRequestItem item) {
        List<OccurrenceSlotDto> own = item.getDate() == null
                ? occurrenceResolver.futureSlotsOnDayOfWeek(item) : List.of();
        Set<LocalDate> dates = item.getDate() != null
                ? Set.of(item.getDate())
                : own.stream().map(OccurrenceSlotDto::date).collect(Collectors.toSet());
        Set<Long> ownOccurrenceIds = own.stream().map(OccurrenceSlotDto::occurrenceId).collect(Collectors.toSet());
        LocalDate min = Collections.min(dates);
        LocalDate max = Collections.max(dates);

        return allocationOccupancyService.findOccupancy(min, max).stream()
                .filter(slot -> dates.contains(slot.date()))
                .filter(slot -> !ownOccurrenceIds.contains(slot.occurrenceId()))
                .filter(slot -> TimeRanges.overlaps(item.getStartTime(), item.endTime(),
                        slot.startTime(), slot.endTime()))
                .map(OccupiedSlot::classroomId)
                .collect(Collectors.toSet());
    }
}
