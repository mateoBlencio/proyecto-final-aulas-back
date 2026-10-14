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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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

        List<ClassroomResponseDto> candidates = candidateClassrooms(item).stream()
                .filter(c -> !(item.getRequiresComputers() && item.getDerivedBuildingId() != null)
                        || item.getDerivedBuildingId().equals(c.buildingId()))
                .toList();
        return toAllowed(candidates, occupiedClassroomIds(item), item.getRequiresComputers());
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

        boolean requiresComputers = item.getRequiresComputers();
        Map<Long, Integer> computersByClassroom = requiresComputers
                ? computersByClassroom(freeByBuilding.values().stream().flatMap(List::stream)
                        .map(ClassroomResponseDto::id).toList())
                : Map.of();

        List<CandidateBuildingDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<ClassroomResponseDto>> entry : freeByBuilding.entrySet()) {
            Long buildingId = entry.getKey();
            if (!buildingIdsWithAuxiliar.contains(buildingId)) {
                continue;
            }
            Integer computers = requiresComputers
                    ? entry.getValue().stream().mapToInt(c -> computersByClassroom.getOrDefault(c.id(), 0)).sum()
                    : null;
            result.add(new CandidateBuildingDto(buildingId, entry.getValue().getFirst().buildingName(),
                    entry.getValue().size(), computers));
        }
        if (requiresComputers) {
            int required = item.getComputerCount();
            result.sort(Comparator
                    .comparing((CandidateBuildingDto b) -> b.availableComputerCount() < required)
                    .thenComparing(CandidateBuildingDto::availableComputerCount, Comparator.reverseOrder()));
        }
        return result;
    }

    record RoomNeeds(LocalDate date, LocalTime startTime, LocalTime endTime,
                     boolean requiresComputers, Integer computerCount, boolean requiresProjector) {
    }

    private static RoomNeeds needsOf(RoomRequestItem item) {
        return new RoomNeeds(item.getDate(), item.getStartTime(), item.endTime(),
                item.getRequiresComputers(), item.getComputerCount(), item.getRequiresProjector());
    }

    List<ClassroomResponseDto> candidateClassrooms(RoomRequestItem item) {
        return candidateClassrooms(needsOf(item));
    }

    List<ClassroomResponseDto> candidateClassrooms(RoomNeeds needs) {
        List<ClassroomResponseDto> candidates = classroomService.findAllAvailable();
        if (needs.requiresComputers()) {
            // Las PCs se suman entre las aulas asignadas: cualquier laboratorio sirve como candidato.
            Set<Long> withComputers = classroomService.findIdsWithResourceAtLeast(COMPUTERS_RESOURCE_NAME, 1);
            candidates = candidates.stream().filter(c -> withComputers.contains(c.id())).toList();
        }
        if (needs.requiresProjector()) {
            Set<Long> withProjector = classroomService.findIdsWithResourceAtLeast(PROJECTOR_RESOURCE_NAME, 1);
            candidates = candidates.stream().filter(c -> withProjector.contains(c.id())).toList();
        }
        return candidates;
    }

    Map<Long, Integer> computersByClassroom(Collection<Long> classroomIds) {
        return classroomService.findResourceAmounts(COMPUTERS_RESOURCE_NAME, classroomIds);
    }

    List<AllowedClassroomDto> findAllowedClassrooms(RoomNeeds needs) {
        return toAllowed(candidateClassrooms(needs), occupiedClassroomIds(needs), needs.requiresComputers());
    }

    Set<Long> occupiedClassroomIds(RoomRequestItem item) {
        if (item.getDate() != null) {
            return occupiedClassroomIds(needsOf(item));
        }
        List<OccurrenceSlotDto> own = occurrenceResolver.futureSlotsOnDayOfWeek(item);
        Set<LocalDate> dates = own.stream().map(OccurrenceSlotDto::date).collect(Collectors.toSet());
        Set<Long> ownOccurrenceIds = own.stream().map(OccurrenceSlotDto::occurrenceId).collect(Collectors.toSet());
        return occupiedClassroomIds(dates, ownOccurrenceIds, item.getStartTime(), item.endTime());
    }

    Set<Long> occupiedClassroomIds(RoomNeeds needs) {
        return occupiedClassroomIds(Set.of(needs.date()), Collections.emptySet(), needs.startTime(), needs.endTime());
    }

    private Set<Long> occupiedClassroomIds(Set<LocalDate> dates, Set<Long> ownOccurrenceIds,
                                           LocalTime startTime, LocalTime endTime) {
        LocalDate min = Collections.min(dates);
        LocalDate max = Collections.max(dates);

        return allocationOccupancyService.findOccupancy(min, max).stream()
                .filter(slot -> dates.contains(slot.date()))
                .filter(slot -> !ownOccurrenceIds.contains(slot.occurrenceId()))
                .filter(slot -> TimeRanges.overlaps(startTime, endTime, slot.startTime(), slot.endTime()))
                .map(OccupiedSlot::classroomId)
                .collect(Collectors.toSet());
    }

    private List<AllowedClassroomDto> toAllowed(List<ClassroomResponseDto> candidates,
                                                Set<Long> occupiedClassroomIds, boolean requiresComputers) {
        Map<Long, Integer> computersByClassroom = requiresComputers
                ? computersByClassroom(candidates.stream().map(ClassroomResponseDto::id).toList())
                : Map.of();
        return candidates.stream()
                .map(classroom -> new AllowedClassroomDto(classroom.id(), classroom.roomNumber(),
                        classroom.buildingId(), classroom.buildingName(), classroom.capacity(),
                        !occupiedClassroomIds.contains(classroom.id()),
                        computersByClassroom.get(classroom.id())))
                .toList();
    }
}
