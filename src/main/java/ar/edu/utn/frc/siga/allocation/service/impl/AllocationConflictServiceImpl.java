package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OverlapConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UnassignedConflictDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.service.AllocationConflictService;
import ar.edu.utn.frc.siga.allocation.model.ConflictType;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.Overcrowding;
import ar.edu.utn.frc.siga.common.util.Paging;
import ar.edu.utn.frc.siga.common.util.RoomDate;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllocationConflictServiceImpl implements AllocationConflictService {

    private final ClassroomService classroomService;
    private final AcademicEventService academicEventService;
    private final AcademicPeriodService academicPeriodService;
    private final AllocationOccupancyReader occupancyReader;
    private final OccurrenceService occurrenceService;
    private final AllocationRepository allocationRepository;

    @Override
    public Page<AllocationConflictDto> findConflicts(Set<ConflictType> types, LocalDate from, LocalDate to,
                                                       boolean includePast, Pageable pageable) {
        Set<ConflictType> effectiveTypes = (types == null || types.isEmpty())
                ? EnumSet.allOf(ConflictType.class) : types;
        Range range = resolveRange(from, to);

        List<AllocationConflictDto> merged = new ArrayList<>();
        if (effectiveTypes.contains(ConflictType.UNASSIGNED)) {
            merged.addAll(buildUnassignedConflicts(range, includePast));
        }
        if (effectiveTypes.contains(ConflictType.OVERCROWDED)) {
            merged.addAll(buildOvercrowdedConflicts(range, includePast));
        }
        if (effectiveTypes.contains(ConflictType.OVERLAP)) {
            merged.addAll(buildOverlapConflicts(range, includePast));
        }
        log.info("Conflictos de asignación listados: types={}, count={}", effectiveTypes, merged.size());
        return Paging.of(merged, pageable);
    }

    @Override
    public List<Long> resolveAllUnassignedEventIds() {
        Range range = resolveRange(null, null);
        List<Long> ids = List.copyOf(unassignedEventIds(range, false));
        log.info("Resolución de selección masiva: eventos sin aula count={}", ids.size());
        return ids;
    }

    private List<UnassignedConflictDto> buildUnassignedConflicts(Range range, boolean includePast) {
        Set<Long> eventIds = unassignedEventIds(range, includePast);
        Map<Long, AcademicEventResponseDto> eventById = fetchEventsById(eventIds);
        return eventIds.stream()
                .map(eventById::get)
                .filter(Objects::nonNull)
                .map(UnassignedConflictDto::new)
                .toList();
    }

    private List<OvercrowdedConflictDto> buildOvercrowdedConflicts(Range range, boolean includePast) {
        List<OccupiedSlot> occupancy = readOccupancy(range, includePast);

        Map<OvercrowdKey, OvercrowdAcc> overcrowdAccs = new LinkedHashMap<>();
        for (OccupiedSlot slot : occupancy) {
            overcrowdAccs.computeIfAbsent(new OvercrowdKey(slot.eventId(), slot.classroomId()),
                            k -> new OvercrowdAcc(slot.eventId(), slot.classroomId()))
                    .dates.add(slot.date());
        }

        Set<Long> eventIds = new LinkedHashSet<>();
        Set<Integer> classroomIds = new LinkedHashSet<>();
        for (OvercrowdAcc acc : overcrowdAccs.values()) {
            eventIds.add(acc.eventId);
            classroomIds.add(acc.classroomId);
        }

        return buildOvercrowded(overcrowdAccs, fetchEventsById(eventIds), fetchClassroomsById(classroomIds));
    }

    private List<OverlapConflictDto> buildOverlapConflicts(Range range, boolean includePast) {
        List<OccupiedSlot> occupancy = readOccupancy(range, includePast);
        Map<OverlapKey, OverlapAcc> overlapAccs = computeOverlaps(occupancy);

        Set<Long> eventIds = new LinkedHashSet<>();
        Set<Integer> classroomIds = new LinkedHashSet<>();
        for (OverlapAcc acc : overlapAccs.values()) {
            eventIds.add(acc.eventIdA);
            eventIds.add(acc.eventIdB);
            classroomIds.add(acc.classroomId);
        }

        return buildOverlaps(overlapAccs, fetchEventsById(eventIds), fetchClassroomsById(classroomIds));
    }

    private Set<Long> unassignedEventIds(Range range, boolean includePast) {
        List<OccurrenceSlotDto> occurrences =
                occurrenceService.findSlotsByStatusBetween(OccurrenceStatus.NEEDS_ROOM, range.from(), range.to());
        Set<Long> occurrenceIds = occurrences.stream().map(OccurrenceSlotDto::occurrenceId).collect(Collectors.toSet());
        Set<Long> allocatedOccurrenceIds = allocationRepository.findByOccurrenceIdIn(occurrenceIds).stream()
                .map(Allocation::getOccurrenceId).collect(Collectors.toSet());

        Set<Long> eventIds = new LinkedHashSet<>();
        for (OccurrenceSlotDto occurrence : occurrences) {
            if (!includePast && occurrence.isPast()) continue;
            if (allocatedOccurrenceIds.contains(occurrence.occurrenceId())) continue;
            eventIds.add(occurrence.eventId());
        }
        return eventIds;
    }

    private List<OccupiedSlot> readOccupancy(Range range, boolean includePast) {
        return occupancyReader.loadAssigned(range.from(), range.to()).stream()
                .filter(slot -> includePast || !isPast(slot))
                .toList();
    }

    private boolean isPast(OccupiedSlot slot) {
        return LocalDateTime.now().isAfter(slot.date().atTime(slot.startTime()));
    }

    private Map<Long, AcademicEventResponseDto> fetchEventsById(Set<Long> eventIds) {
        return Maps.byId(academicEventService.findByIds(eventIds), AcademicEventResponseDto::id);
    }

    private Map<Integer, ClassroomResponseDto> fetchClassroomsById(Set<Integer> ids) {
        return Maps.byId(classroomService.findByIds(ids), ClassroomResponseDto::id);
    }

    private Range resolveRange(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = DateRanges.defaultFrom(from);
        LocalDate effectiveTo = to != null ? to : resolveDefaultTo(effectiveFrom);
        DateRanges.requireNotBefore(effectiveTo, effectiveFrom);
        log.debug("Rango de conflictos de asignación: from={}, to={}", effectiveFrom, effectiveTo);
        return new Range(effectiveFrom, effectiveTo);
    }

    private LocalDate resolveDefaultTo(LocalDate from) {
        return academicPeriodService.findActive().stream()
                .map(AcademicPeriodResponseDto::endDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> from.plusMonths(6));
    }

    private record OverlapHit(OverlapKey key, LocalDate date) {
    }

    private Map<OverlapKey, OverlapAcc> computeOverlaps(List<OccupiedSlot> occupancy) {
        List<OverlapHit> hits = Clashes.within(occupancy,
                slot -> List.of(new RoomDate(slot.classroomId(), slot.date())),
                (a, b) -> !a.eventId().equals(b.eventId()),
                (a, b, key) -> {
                    Long firstId = a.eventId() <= b.eventId() ? a.eventId() : b.eventId();
                    Long secondId = a.eventId() <= b.eventId() ? b.eventId() : a.eventId();
                    return new OverlapHit(new OverlapKey(firstId, secondId, key.classroomId()), key.date());
                });

        Map<OverlapKey, OverlapAcc> overlapAccs = new LinkedHashMap<>();
        for (OverlapHit hit : hits) {
            OverlapKey key = hit.key();
            overlapAccs.computeIfAbsent(key, k -> new OverlapAcc(k.eventIdA(), k.eventIdB(), k.classroomId()))
                    .dates.add(hit.date());
        }
        return overlapAccs;
    }

    private List<OvercrowdedConflictDto> buildOvercrowded(Map<OvercrowdKey, OvercrowdAcc> overcrowdAccs,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Integer, ClassroomResponseDto> classroomDtoById) {
        List<OvercrowdedConflictDto> overcrowded = new ArrayList<>();
        for (OvercrowdAcc acc : overcrowdAccs.values()) {
            ClassroomResponseDto classroom = classroomDtoById.get(acc.classroomId);
            if (classroom == null || classroom.capacity() == null) continue;

            AcademicEventResponseDto event = eventDtoById.get(acc.eventId);
            Integer enrolled = event != null && event.enrolled() != null ? event.enrolled() : 0;
            Integer capacity = classroom.capacity();
            Integer overcrowdedBy = Overcrowding.by(enrolled, capacity);
            if (overcrowdedBy == null || overcrowdedBy == 0) continue;

            overcrowded.add(new OvercrowdedConflictDto(
                    event, classroom, enrolled, capacity, overcrowdedBy, List.copyOf(acc.dates)));
        }
        return overcrowded;
    }

    private List<OverlapConflictDto> buildOverlaps(Map<OverlapKey, OverlapAcc> overlapAccs,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Integer, ClassroomResponseDto> classroomDtoById) {
        List<OverlapConflictDto> overlaps = new ArrayList<>();
        for (OverlapAcc acc : overlapAccs.values()) {
            overlaps.add(new OverlapConflictDto(
                    classroomDtoById.get(acc.classroomId),
                    eventDtoById.get(acc.eventIdA),
                    eventDtoById.get(acc.eventIdB),
                    List.copyOf(acc.dates)));
        }
        return overlaps;
    }

    private record Range(LocalDate from, LocalDate to) {
    }

    private record OvercrowdKey(Long eventId, Integer classroomId) {
    }

    private static final class OvercrowdAcc {
        final Long eventId;
        final Integer classroomId;
        final Set<LocalDate> dates = new TreeSet<>();

        OvercrowdAcc(Long eventId, Integer classroomId) {
            this.eventId = eventId;
            this.classroomId = classroomId;
        }
    }

    private record OverlapKey(Long eventIdA, Long eventIdB, Integer classroomId) {
    }

    private static final class OverlapAcc {
        final Long eventIdA;
        final Long eventIdB;
        final Integer classroomId;
        final Set<LocalDate> dates = new TreeSet<>();

        OverlapAcc(Long eventIdA, Long eventIdB, Integer classroomId) {
            this.eventIdA = eventIdA;
            this.eventIdB = eventIdB;
            this.classroomId = classroomId;
        }
    }
}
