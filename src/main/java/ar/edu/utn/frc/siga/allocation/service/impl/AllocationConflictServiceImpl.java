package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.NotPermittedConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OverlapConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UnallocatedConflictDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.service.AllocationConflictService;
import ar.edu.utn.frc.siga.allocation.model.ConflictType;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.Overcrowding;
import ar.edu.utn.frc.siga.common.util.Paging;
import ar.edu.utn.frc.siga.common.util.RoomDate;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.DayOfWeek;
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
    private final BuildingScopeResolver buildingScopeResolver;

    @Override
    public Page<AllocationConflictDto> findConflicts(Set<ConflictType> types, LocalDate from, LocalDate to,
                                                       boolean includePast, String q, DayOfWeek dayOfWeek,
                                                       String specialty, Pageable pageable) {
        Set<ConflictType> effectiveTypes = (types == null || types.isEmpty())
                ? EnumSet.allOf(ConflictType.class) : types;
        Range range = resolveRange(from, to);

        List<AllocationConflictDto> merged = new ArrayList<>();
        if (effectiveTypes.contains(ConflictType.UNALLOCATED)) {
            UnallocatedConflictComparator.validate(pageable.getSort());
            merged.addAll(buildUnallocatedConflicts(range, includePast, q, dayOfWeek, specialty, pageable.getSort()));
        }
        if (effectiveTypes.stream().anyMatch(t -> t != ConflictType.UNALLOCATED)) {
            List<OccupiedSlot> occupancy = readOccupancy(range, includePast);
            if (effectiveTypes.contains(ConflictType.OVERCROWDED)) {
                merged.addAll(buildOvercrowdedConflicts(occupancy));
            }
            if (effectiveTypes.contains(ConflictType.OVERLAP)) {
                merged.addAll(buildOverlapConflicts(occupancy));
            }
            if (effectiveTypes.contains(ConflictType.NOT_PERMITTED)) {
                merged.addAll(buildNotPermittedConflicts(occupancy));
            }
        }

        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.CONFLICT_READ);
        merged.removeIf(dto -> !switch (dto) {
            case UnallocatedConflictDto ignored -> true;
            case OvercrowdedConflictDto o -> scope.allows(o.classroom().buildingId());
            case OverlapConflictDto o -> scope.allows(o.classroom().buildingId());
            case NotPermittedConflictDto o -> scope.allows(o.classroom().buildingId());
        });

        log.info("Conflictos de asignación listados: types={}, count={}", effectiveTypes, merged.size());
        return Paging.of(merged, pageable);
    }

    @Override
    public List<Long> resolveAllUnallocatedEventIds() {
        Range range = resolveRange(null, null);
        List<Long> ids = List.copyOf(unallocatedEventIds(range, false));
        log.info("Resolución de selección masiva: eventos sin aula count={}", ids.size());
        return ids;
    }

    private List<UnallocatedConflictDto> buildUnallocatedConflicts(Range range, boolean includePast, String q,
                                                                   DayOfWeek dayOfWeek, String specialty, Sort sort) {
        Set<Long> eventIds = unallocatedEventIds(range, includePast);
        Map<Long, AcademicEventResponseDto> eventById = fetchEventsById(eventIds);
        return eventIds.stream()
                .map(eventById::get)
                .filter(Objects::nonNull)
                .map(UnallocatedConflictDto::new)
                .filter(c -> matches(c.event(), q, dayOfWeek, specialty))
                .sorted(UnallocatedConflictComparator.of(sort))
                .toList();
    }

    private static boolean matches(AcademicEventResponseDto event, String q, DayOfWeek dayOfWeek, String specialty) {
        if (dayOfWeek != null && UnallocatedConflictComparator.dayOf(event) != dayOfWeek) {
            return false;
        }
        var subject = event.subject();
        String specialtyName = subject == null ? null : subject.studyPlan().specialty().name();
        if (specialty != null && !specialty.equals(specialtyName)) {
            return false;
        }
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = fold(q.strip());
        CommissionResponseDto commission = switch (event) {
            case RecurringEventResponseDto r -> r.commission();
            case UniqueEventResponseDto u -> u.commission();
        };
        return fold(subject == null ? null : subject.name()).contains(needle)
                || fold(specialtyName).contains(needle)
                || (commission != null && fold(commission.courseCode()).contains(needle));
    }

    private static String fold(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    private static Map<EventRoomKey, EventRoomAcc> groupByEventRoom(List<OccupiedSlot> occupancy) {
        Map<EventRoomKey, EventRoomAcc> accs = new LinkedHashMap<>();
        for (OccupiedSlot slot : occupancy) {
            accs.computeIfAbsent(new EventRoomKey(slot.eventId(), slot.classroomId()),
                            k -> new EventRoomAcc(slot.eventId(), slot.classroomId()))
                    .dates.add(slot.date());
        }
        return accs;
    }

    private List<OvercrowdedConflictDto> buildOvercrowdedConflicts(List<OccupiedSlot> occupancy) {
        Map<EventRoomKey, EventRoomAcc> overcrowdAccs = groupByEventRoom(occupancy);

        Set<Long> eventIds = new LinkedHashSet<>();
        Set<Long> classroomIds = new LinkedHashSet<>();
        for (EventRoomAcc acc : overcrowdAccs.values()) {
            eventIds.add(acc.eventId);
            classroomIds.add(acc.classroomId);
        }

        return buildOvercrowded(overcrowdAccs, fetchEventsById(eventIds), fetchClassroomsById(classroomIds));
    }

    private List<OverlapConflictDto> buildOverlapConflicts(List<OccupiedSlot> occupancy) {
        Map<OverlapKey, OverlapAcc> overlapAccs = computeOverlaps(occupancy);

        Set<Long> eventIds = new LinkedHashSet<>();
        Set<Long> classroomIds = new LinkedHashSet<>();
        for (OverlapAcc acc : overlapAccs.values()) {
            eventIds.add(acc.eventIdA);
            eventIds.add(acc.eventIdB);
            classroomIds.add(acc.classroomId);
        }

        return buildOverlaps(overlapAccs, fetchEventsById(eventIds), fetchClassroomsById(classroomIds));
    }

    private List<NotPermittedConflictDto> buildNotPermittedConflicts(List<OccupiedSlot> occupancy) {
        Map<EventRoomKey, EventRoomAcc> accs = groupByEventRoom(occupancy);
        if (accs.isEmpty()) {
            return List.of();
        }

        Set<Long> eventIds = new LinkedHashSet<>();
        Set<Long> classroomIds = new LinkedHashSet<>();
        for (EventRoomAcc acc : accs.values()) {
            eventIds.add(acc.eventId);
            classroomIds.add(acc.classroomId);
        }

        Map<Long, AcademicEventResponseDto> eventById = fetchEventsById(eventIds);
        Map<Long, ClassroomResponseDto> classroomById = fetchClassroomsById(classroomIds);
        Map<Long, ClassroomSubjectPermissionDto> permissionByClassroom =
                classroomService.findSubjectPermissions(classroomIds);

        List<NotPermittedConflictDto> conflicts = new ArrayList<>();
        for (EventRoomAcc acc : accs.values()) {
            AcademicEventResponseDto event = eventById.get(acc.eventId);
            ClassroomResponseDto classroom = classroomById.get(acc.classroomId);
            if (event == null || classroom == null || event.subject() == null) continue;

            ClassroomSubjectPermissionDto permission = permissionByClassroom.get(acc.classroomId);
            Long subjectId = event.subject().id();
            if (permission != null && !permission.permits(subjectId)) {
                conflicts.add(new NotPermittedConflictDto(event, classroom, subjectId, List.copyOf(acc.dates)));
            }
        }
        return conflicts;
    }

    private Set<Long> unallocatedEventIds(Range range, boolean includePast) {
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
        return occupancyReader.loadAllocated(range.from(), range.to()).stream()
                .filter(slot -> includePast || !isPast(slot))
                .toList();
    }

    private boolean isPast(OccupiedSlot slot) {
        return LocalDateTime.now().isAfter(slot.date().atTime(slot.startTime()));
    }

    private Map<Long, AcademicEventResponseDto> fetchEventsById(Set<Long> eventIds) {
        return Maps.byId(academicEventService.findByIds(eventIds), AcademicEventResponseDto::id);
    }

    private Map<Long, ClassroomResponseDto> fetchClassroomsById(Set<Long> ids) {
        return Maps.byId(classroomService.findByIdsIncludingDeactivated(ids), ClassroomResponseDto::id);
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
                    Long firstId = Math.min(a.eventId(), b.eventId());
                    Long secondId = Math.max(a.eventId(), b.eventId());
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

    private List<OvercrowdedConflictDto> buildOvercrowded(Map<EventRoomKey, EventRoomAcc> overcrowdAccs,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Long, ClassroomResponseDto> classroomDtoById) {
        List<OvercrowdedConflictDto> overcrowded = new ArrayList<>();
        for (EventRoomAcc acc : overcrowdAccs.values()) {
            ClassroomResponseDto classroom = classroomDtoById.get(acc.classroomId);
            if (classroom == null || classroom.capacity() == null) continue;

            AcademicEventResponseDto event = eventDtoById.get(acc.eventId);
            int enrolled = event != null && event.enrolled() != null ? event.enrolled() : 0;
            Integer capacity = classroom.capacity();
            Integer overcrowdedBy = Overcrowding.by(enrolled, capacity);
            if (overcrowdedBy == null || overcrowdedBy == 0) continue;

            overcrowded.add(new OvercrowdedConflictDto(
                    event, classroom, enrolled, capacity, overcrowdedBy, List.copyOf(acc.dates)));
        }
        return overcrowded;
    }

    private List<OverlapConflictDto> buildOverlaps(Map<OverlapKey, OverlapAcc> overlapAccs,
            Map<Long, AcademicEventResponseDto> eventDtoById, Map<Long, ClassroomResponseDto> classroomDtoById) {
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

    private record EventRoomKey(Long eventId, Long classroomId) {
    }

    private static final class EventRoomAcc {
        final Long eventId;
        final Long classroomId;
        final Set<LocalDate> dates = new TreeSet<>();

        EventRoomAcc(Long eventId, Long classroomId) {
            this.eventId = eventId;
            this.classroomId = classroomId;
        }
    }

    private record OverlapKey(Long eventIdA, Long eventIdB, Long classroomId) {
    }

    private static final class OverlapAcc {
        final Long eventIdA;
        final Long eventIdB;
        final Long classroomId;
        final Set<LocalDate> dates = new TreeSet<>();

        OverlapAcc(Long eventIdA, Long eventIdB, Long classroomId) {
            this.eventIdA = eventIdA;
            this.eventIdB = eventIdB;
            this.classroomId = classroomId;
        }
    }
}
