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
import ar.edu.utn.frc.siga.space.dto.BuildingFilter;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomResources;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
class RoomRequestCandidateResolver {

    private static final String MISSING_PROJECTOR = "PROJECTOR";
    private static final String MISSING_COMPUTERS = "COMPUTERS";

    private final RoomRequestItemRepository itemRepository;
    private final ClassroomService classroomService;
    private final AllocationOccupancyService allocationOccupancyService;
    private final UserService userService;
    private final BuildingService buildingService;
    private final RoomRequestOccurrenceResolver occurrenceResolver;

    @Transactional(readOnly = true)
    List<AllowedClassroomDto> findAllowedClassrooms(Long itemId) {
        log.debug("Buscando aulas candidatas para itemId={}", itemId);

        RoomRequestItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("RoomRequestItem", itemId));

        List<ClassroomResponseDto> candidates = candidateClassrooms(item).stream()
                .filter(c -> item.getDerivedBuildingId() == null || item.getDerivedBuildingId().equals(c.buildingId()))
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
                .collect(Collectors.groupingBy(ClassroomResponseDto::buildingId));

        List<BuildingResponseDto> buildings = buildingService
                .findAll(new BuildingFilter(null), Pageable.unpaged(), false).getContent();
        Set<Long> buildingIdsWithAuxiliar = userService.findBuildingIdsCoveredByRole(
                SystemRole.AUXILIAR_AULICO, buildings.stream().map(BuildingResponseDto::id).toList());

        boolean requiresComputers = item.getRequiresComputers();
        Map<Long, Integer> computersByClassroom = requiresComputers
                ? computersByClassroom(freeByBuilding.values().stream().flatMap(List::stream)
                        .map(ClassroomResponseDto::id).toList())
                : Map.of();

        List<CandidateBuildingDto> result = new ArrayList<>();
        for (BuildingResponseDto building : buildings) {
            if (!buildingIdsWithAuxiliar.contains(building.id())) {
                continue;
            }
            List<ClassroomResponseDto> free = freeByBuilding.getOrDefault(building.id(), List.of());
            Integer computers = requiresComputers
                    ? free.stream().mapToInt(c -> computersByClassroom.getOrDefault(c.id(), 0)).sum()
                    : null;
            result.add(new CandidateBuildingDto(building.id(), building.name(), free.size(), computers));
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
            Set<Long> withComputers = classroomService.findIdsWithResourceAtLeast(ClassroomResources.COMPUTERS, 1);
            candidates = candidates.stream().filter(c -> withComputers.contains(c.id())).toList();
        }
        if (needs.requiresProjector()) {
            Set<Long> withProjector = classroomService.findIdsWithResourceAtLeast(ClassroomResources.PROJECTOR, 1);
            candidates = candidates.stream().filter(c -> withProjector.contains(c.id())).toList();
        }
        return candidates;
    }

    Map<Long, Integer> computersByClassroom(Collection<Long> classroomIds) {
        return classroomService.findResourceAmounts(ClassroomResources.COMPUTERS, classroomIds);
    }

    /** Todas las aulas activas del alcance, las que cumplen lo pedido primero. */
    List<AllowedClassroomDto> findAllWithMissingRequirements(RoomNeeds needs) {
        List<ClassroomResponseDto> all = classroomService.findAllAvailable();
        Set<Long> withComputers = needs.requiresComputers()
                ? classroomService.findIdsWithResourceAtLeast(ClassroomResources.COMPUTERS, 1) : Set.of();
        Set<Long> withProjector = needs.requiresProjector()
                ? classroomService.findIdsWithResourceAtLeast(ClassroomResources.PROJECTOR, 1) : Set.of();
        Set<Long> occupied = occupiedClassroomIds(needs);
        Map<Long, Integer> computersByClassroom = needs.requiresComputers()
                ? computersByClassroom(all.stream().map(ClassroomResponseDto::id).toList())
                : Map.of();
        return all.stream()
                .map(classroom -> {
                    List<String> missing = new ArrayList<>();
                    if (needs.requiresProjector() && !withProjector.contains(classroom.id())) {
                        missing.add(MISSING_PROJECTOR);
                    }
                    if (needs.requiresComputers() && !withComputers.contains(classroom.id())) {
                        missing.add(MISSING_COMPUTERS);
                    }
                    return new AllowedClassroomDto(classroom.id(), classroom.roomNumber(), classroom.buildingId(),
                            classroom.buildingName(), classroom.capacity(), !occupied.contains(classroom.id()),
                            computersByClassroom.get(classroom.id()), missing);
                })
                .sorted(Comparator.comparing((AllowedClassroomDto c) -> !c.missingRequirements().isEmpty()))
                .toList();
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
                        computersByClassroom.get(classroom.id()), List.of()))
                .toList();
    }
}
