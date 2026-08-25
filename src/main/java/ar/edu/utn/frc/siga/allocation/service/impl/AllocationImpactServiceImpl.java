package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationImpactResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ImpactBlockerDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ImpactConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ImpactOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.BlockerKind;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationImpactService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.validator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.util.TimeRanges;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
class AllocationImpactServiceImpl implements AllocationImpactService {

    private static final AllocationImpactResponseDto EMPTY =
            new AllocationImpactResponseDto(0, 0, 0, List.of(), List.of());

    private final AllocationTargetResolver targetResolver;
    private final AllocationValidator validator;
    private final AllocationOccupancyReader occupancyReader;
    private final AllocationRepository allocationRepository;
    private final ClassroomService classroomService;
    private final AcademicEventService academicEventService;

    @Override
    @Transactional(readOnly = true)
    public AllocationImpactResponseDto analyze(AllocationCommand command) {
        LocalDate clampFrom = command.source() == AllocationSource.IMPORTED ? null : LocalDate.now();
        Map<OccurrenceSlotDto, Integer> classroomByOccurrence =
                targetResolver.resolveClassroomByOccurrence(command.items(), clampFrom);

        if (classroomByOccurrence.isEmpty()) {
            return EMPTY;
        }

        validator.validateClassroomsAvailable(Set.copyOf(classroomByOccurrence.values()));

        List<AllocationCandidate> candidates = classroomByOccurrence.entrySet().stream()
                .map(e -> new AllocationCandidate(e.getKey(), e.getValue()))
                .toList();

        List<OccurrenceConflictDto> conflicts = validator.findConflicts(candidates);
        Set<Long> blockedOccurrenceIds = conflicts.stream()
                .map(OccurrenceConflictDto::occurrenceId)
                .collect(Collectors.toSet());

        List<ImpactOccurrenceDto> occurrences = describeOccurrences(candidates, blockedOccurrenceIds);
        List<ImpactConflictDto> detailed = describeConflicts(conflicts, candidates);

        log.debug("Impacto analizado: total={}, bloqueadas={}", occurrences.size(), blockedOccurrenceIds.size());
        return new AllocationImpactResponseDto(
                occurrences.size(),
                occurrences.size() - blockedOccurrenceIds.size(),
                blockedOccurrenceIds.size(),
                occurrences,
                detailed);
    }

    private List<ImpactOccurrenceDto> describeOccurrences(List<AllocationCandidate> candidates,
                                                          Set<Long> blockedOccurrenceIds) {
        Set<Long> occurrenceIds = candidates.stream()
                .map(c -> c.occurrence().occurrenceId())
                .collect(Collectors.toSet());
        Map<Long, Integer> currentRoomByOccurrence = allocationRepository.findByOccurrenceIdIn(occurrenceIds).stream()
                .collect(Collectors.toMap(Allocation::getOccurrenceId, Allocation::getClassroomId, (x, y) -> x));

        return candidates.stream()
                .sorted(Comparator.comparing((AllocationCandidate c) -> c.occurrence().date())
                        .thenComparing(AllocationCandidate::startTime))
                .map(c -> new ImpactOccurrenceDto(
                        c.occurrence().occurrenceId(),
                        c.occurrence().eventId(),
                        c.occurrence().date(),
                        c.startTime(),
                        c.endTime(),
                        currentRoomByOccurrence.get(c.occurrence().occurrenceId()),
                        c.classroomId(),
                        blockedOccurrenceIds.contains(c.occurrence().occurrenceId())))
                .toList();
    }

    // Si no hay conflictos no se consulta nada: el camino feliz no paga el costo.
    private List<ImpactConflictDto> describeConflicts(List<OccurrenceConflictDto> conflicts,
                                                      List<AllocationCandidate> candidates) {
        if (conflicts.isEmpty()) {
            return List.of();
        }

        Map<Long, EventType> typeByEventId = blockerTypes(conflicts);
        List<ClassroomResponseDto> availableRooms = classroomService.findAllAvailable();

        LocalDate min = conflicts.stream().map(OccurrenceConflictDto::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = conflicts.stream().map(OccurrenceConflictDto::date).max(Comparator.naturalOrder()).orElseThrow();
        List<OccupiedSlot> occupancy = occupancyReader.loadAllocated(min, max);
        Set<Long> movingOccurrenceIds = candidates.stream()
                .map(c -> c.occurrence().occurrenceId())
                .collect(Collectors.toSet());

        return conflicts.stream()
                .map(c -> new ImpactConflictDto(
                        c.occurrenceId(), c.date(), c.startTime(), c.endTime(), c.classroomId(),
                        new ImpactBlockerDto(
                                c.conflictingAllocationId() == null
                                        ? BlockerKind.SAME_BATCH
                                        : BlockerKind.EXISTING_ALLOCATION,
                                c.conflictingEventId(),
                                typeByEventId.get(c.conflictingEventId()),
                                c.conflictingOccurrenceId(),
                                c.conflictingAllocationId()),
                        freeRoomsAt(c, occupancy, movingOccurrenceIds, candidates, availableRooms)))
                .toList();
    }

    private Map<Long, EventType> blockerTypes(List<OccurrenceConflictDto> conflicts) {
        Set<Long> eventIds = conflicts.stream()
                .map(OccurrenceConflictDto::conflictingEventId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return academicEventService.findByIds(eventIds).stream()
                .collect(Collectors.toMap(AcademicEventResponseDto::id, AcademicEventResponseDto::type,
                        (x, y) -> x));
    }

    // Libres DESPUÉS de aplicar el pedido: las ocurrencias que se mueven liberan su aula vieja,
    // y las aulas que el propio pedido ocupa cuentan como tomadas.
    private List<ClassroomResponseDto> freeRoomsAt(OccurrenceConflictDto conflict,
                                                   List<OccupiedSlot> occupancy,
                                                   Set<Long> movingOccurrenceIds,
                                                   List<AllocationCandidate> candidates,
                                                   List<ClassroomResponseDto> availableRooms) {
        Set<Integer> taken = new HashSet<>();

        for (OccupiedSlot slot : occupancy) {
            if (movingOccurrenceIds.contains(slot.occurrenceId())) continue;
            if (!slot.date().equals(conflict.date())) continue;
            if (TimeRanges.overlaps(conflict.startTime(), conflict.endTime(), slot.startTime(), slot.endTime())) {
                taken.add(slot.classroomId());
            }
        }
        for (AllocationCandidate candidate : candidates) {
            if (candidate.occurrence().occurrenceId().equals(conflict.occurrenceId())) continue;
            if (!candidate.occurrence().date().equals(conflict.date())) continue;
            if (TimeRanges.overlaps(conflict.startTime(), conflict.endTime(),
                    candidate.startTime(), candidate.endTime())) {
                taken.add(candidate.classroomId());
            }
        }

        return availableRooms.stream()
                .filter(room -> !taken.contains(room.id()))
                .sorted(Comparator.comparing(ClassroomResponseDto::roomNumber,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
