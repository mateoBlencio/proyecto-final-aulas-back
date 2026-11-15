package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.NotPermittedIssueDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardClassroomDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardDateDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardEventDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardSlotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardSummaryDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceIssueDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedIssueDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OverlapIssueDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UnallocatedIssueDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceIssueType;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.OccurrenceBoardService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.Overcrowding;
import ar.edu.utn.frc.siga.common.util.Paging;
import ar.edu.utn.frc.siga.common.util.RoomDate;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OccurrenceBoardServiceImpl implements OccurrenceBoardService {

    private final OccurrenceService occurrenceService;
    private final AllocationRepository allocationRepository;
    private final AcademicEventService academicEventService;
    private final ClassroomService classroomService;
    private final BuildingScopeResolver buildingScopeResolver;
    private final ConflictDateRange dateRange;

    @Override
    public Page<OccurrenceBoardEventDto> findBoard(LocalDate from, LocalDate to, boolean onlyFuture,
                                                   Set<OccurrenceIssueType> issues, String q, DayOfWeek dayOfWeek,
                                                   String specialty, Pageable pageable) {
        UnallocatedConflictComparator.validate(pageable.getSort());
        ConflictDateRange.Range range = onlyFuture
                ? dateRange.resolve(from, to) : dateRange.resolveFromPeriodStart(from, to);

        List<OccurrenceSlotDto> slots = occurrenceService.findSlotsBetween(range.from(), range.to()).stream()
                .filter(o -> !(o.status() == OccurrenceStatus.ROOM_RELEASED
                        && o.releaseReason() == ReleaseReason.MIRROR_RETIRED))
                .toList();
        Map<Long, AcademicEventResponseDto> eventById = Maps.byId(
                academicEventService.findByIds(ids(slots, OccurrenceSlotDto::eventId)),
                AcademicEventResponseDto::id);
        Set<Long> matchingEventIds = eventById.values().stream()
                .filter(e -> AcademicEventMatcher.matches(e, q, dayOfWeek, specialty))
                .map(AcademicEventResponseDto::id)
                .collect(Collectors.toSet());
        Board board = loadBoard(slots, eventById);

        Map<Long, Map<LocalDate, List<OccurrenceSlotDto>>> slotsByEventDate = slots.stream()
                .filter(o -> matchingEventIds.contains(o.eventId()))
                .filter(o -> !(onlyFuture && o.isPast()))
                .collect(Collectors.groupingBy(OccurrenceSlotDto::eventId, LinkedHashMap::new,
                        Collectors.groupingBy(OccurrenceSlotDto::date, TreeMap::new, Collectors.toList())));

        List<OccurrenceBoardEventDto> items = new ArrayList<>();
        slotsByEventDate.forEach((eventId, slotsByDate) -> {
            AcademicEventResponseDto event = eventById.get(eventId);
            List<OccurrenceBoardDateDto> dates = slotsByDate.values().stream()
                    .flatMap(group -> buildDate(group, event, board).stream())
                    .toList();
            List<OccurrenceBoardDateDto> shown = dates.stream().filter(d -> matchesIssues(d, issues)).toList();
            if (!shown.isEmpty()) {
                items.add(new OccurrenceBoardEventDto(event, summarize(dates), shown));
            }
        });
        items.sort(UnallocatedConflictComparator.of(pageable.getSort(), OccurrenceBoardEventDto::event));

        log.info("Tablero de ocurrencias listado: onlyFuture={}, issues={}, eventos={}",
                onlyFuture, issues, items.size());
        return Paging.of(items, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
    }

    private Board loadBoard(List<OccurrenceSlotDto> slots, Map<Long, AcademicEventResponseDto> eventById) {
        Map<Long, Allocation> allocationByOccurrence = Maps.byId(
                allocationRepository.findByOccurrenceIdIn(ids(slots, OccurrenceSlotDto::occurrenceId)),
                Allocation::getOccurrenceId);
        Set<Long> allocatedClassroomIds = allocationByOccurrence.values().stream()
                .map(Allocation::getClassroomId)
                .collect(Collectors.toSet());
        Set<Long> classroomIds = new HashSet<>(allocatedClassroomIds);
        slots.stream()
                .filter(o -> !allocationByOccurrence.containsKey(o.occurrenceId()))
                .map(OccurrenceSlotDto::previousClassroomId)
                .filter(Objects::nonNull)
                .forEach(classroomIds::add);

        return new Board(allocationByOccurrence,
                Maps.byId(classroomService.findByIdsIncludingDeactivated(classroomIds), ClassroomResponseDto::id),
                classroomService.findSubjectPermissions(allocatedClassroomIds),
                overlapsByOccurrence(slots, allocationByOccurrence, eventById),
                buildingScopeResolver.scopeFor(Permission.CONFLICT_READ));
    }

    private static Map<Long, List<OverlapIssueDto>> overlapsByOccurrence(List<OccurrenceSlotDto> slots,
            Map<Long, Allocation> allocationByOccurrence, Map<Long, AcademicEventResponseDto> eventById) {
        List<OccupiedSlot> occupancy = slots.stream()
                .filter(o -> allocationByOccurrence.containsKey(o.occurrenceId()))
                .map(o -> OccupiedSlot.from(allocationByOccurrence.get(o.occurrenceId()), o))
                .toList();
        return Clashes.within(occupancy,
                        slot -> List.of(new RoomDate(slot.classroomId(), slot.date())),
                        (a, b) -> !a.eventId().equals(b.eventId()),
                        (a, b, key) -> List.of(overlap(a, b, eventById), overlap(b, a, eventById)))
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(OverlapIssueDto::occurrenceId));
    }

    private static OverlapIssueDto overlap(OccupiedSlot slot, OccupiedSlot other,
                                           Map<Long, AcademicEventResponseDto> eventById) {
        return new OverlapIssueDto(slot.occurrenceId(), slot.classroomId(), eventById.get(other.eventId()));
    }

    private static Optional<OccurrenceBoardDateDto> buildDate(List<OccurrenceSlotDto> group,
                                                              AcademicEventResponseDto event, Board board) {
        List<OccurrenceSlotDto> sorted = group.stream()
                .sorted(Comparator.comparing(OccurrenceSlotDto::roomSlot))
                .toList();
        List<OccurrenceSlotDto> visible = sorted.stream().filter(board::isVisible).toList();
        if (visible.isEmpty()) {
            return Optional.empty();
        }

        List<OccurrenceIssueDto> issues = new ArrayList<>();
        long missingRooms = sorted.stream()
                .filter(o -> o.status() == OccurrenceStatus.NEEDS_ROOM && board.allocationOf(o) == null)
                .count();
        if (missingRooms > 0) {
            issues.add(new UnallocatedIssueDto((int) missingRooms));
        }
        if (visible.stream().anyMatch(o -> board.allocationOf(o) != null)) {
            overcrowded(sorted, event, board).ifPresent(issues::add);
        }
        for (OccurrenceSlotDto slot : visible) {
            issues.addAll(board.overlapsByOccurrence().getOrDefault(slot.occurrenceId(), List.of()));
            notPermitted(slot, event, board).ifPresent(issues::add);
        }

        OccurrenceSlotDto first = sorted.getFirst();
        int requiredRooms = (int) sorted.stream().filter(o -> o.status() == OccurrenceStatus.NEEDS_ROOM).count();
        return Optional.of(new OccurrenceBoardDateDto(first.date(), first.startTime(), first.endTime(),
                first.isPast(), requiredRooms, visible.stream().map(board::toSlot).toList(), issues));
    }

    private static Optional<OccurrenceIssueDto> overcrowded(List<OccurrenceSlotDto> group,
                                                            AcademicEventResponseDto event, Board board) {
        List<ClassroomResponseDto> classrooms = group.stream()
                .map(board::allocationOf)
                .filter(Objects::nonNull)
                .map(Allocation::getClassroomId)
                .distinct()
                .map(board.classroomById()::get)
                .toList();
        if (classrooms.stream().anyMatch(c -> c == null || c.capacity() == null)) {
            return Optional.empty();
        }
        int enrolled = event.enrolled() != null ? event.enrolled() : 0;
        int capacity = classrooms.stream().mapToInt(ClassroomResponseDto::capacity).sum();
        int overcrowdedBy = Overcrowding.by(enrolled, capacity);
        return overcrowdedBy > 0
                ? Optional.of(new OvercrowdedIssueDto(enrolled, capacity, overcrowdedBy))
                : Optional.empty();
    }

    private static Optional<OccurrenceIssueDto> notPermitted(OccurrenceSlotDto slot, AcademicEventResponseDto event,
                                                             Board board) {
        Allocation allocation = board.allocationOf(slot);
        if (allocation == null || event.subject() == null) {
            return Optional.empty();
        }
        ClassroomSubjectPermissionDto permission = board.permissionByClassroom().get(allocation.getClassroomId());
        return permission != null && !permission.permits(event.subject().id())
                ? Optional.of(new NotPermittedIssueDto(slot.occurrenceId(), allocation.getClassroomId()))
                : Optional.empty();
    }

    private static OccurrenceBoardSummaryDto summarize(List<OccurrenceBoardDateDto> dates) {
        return new OccurrenceBoardSummaryDto(dates.size(),
                countWith(dates, OccurrenceIssueType.UNALLOCATED),
                countWith(dates, OccurrenceIssueType.RELEASED),
                countWith(dates, OccurrenceIssueType.OVERLAP),
                countWith(dates, OccurrenceIssueType.OVERCROWDED),
                countWith(dates, OccurrenceIssueType.NOT_PERMITTED));
    }

    private static int countWith(List<OccurrenceBoardDateDto> dates, OccurrenceIssueType type) {
        return (int) dates.stream().filter(d -> typesOf(d).contains(type)).count();
    }

    private static boolean matchesIssues(OccurrenceBoardDateDto date, Set<OccurrenceIssueType> issues) {
        return issues == null || issues.isEmpty() || !Collections.disjoint(typesOf(date), issues);
    }

    private static Set<OccurrenceIssueType> typesOf(OccurrenceBoardDateDto date) {
        Set<OccurrenceIssueType> types = EnumSet.noneOf(OccurrenceIssueType.class);
        date.issues().forEach(issue -> types.add(typeOf(issue)));
        if (date.slots().stream().anyMatch(s -> s.status() == OccurrenceStatus.ROOM_RELEASED)) {
            types.add(OccurrenceIssueType.RELEASED);
        }
        return types;
    }

    private static OccurrenceIssueType typeOf(OccurrenceIssueDto issue) {
        return switch (issue) {
            case UnallocatedIssueDto ignored -> OccurrenceIssueType.UNALLOCATED;
            case OvercrowdedIssueDto ignored -> OccurrenceIssueType.OVERCROWDED;
            case OverlapIssueDto ignored -> OccurrenceIssueType.OVERLAP;
            case NotPermittedIssueDto ignored -> OccurrenceIssueType.NOT_PERMITTED;
        };
    }

    private static Set<Long> ids(List<OccurrenceSlotDto> slots, Function<OccurrenceSlotDto, Long> id) {
        return slots.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private record Board(Map<Long, Allocation> allocationByOccurrence,
                         Map<Long, ClassroomResponseDto> classroomById,
                         Map<Long, ClassroomSubjectPermissionDto> permissionByClassroom,
                         Map<Long, List<OverlapIssueDto>> overlapsByOccurrence,
                         BuildingScope scope) {

        Allocation allocationOf(OccurrenceSlotDto slot) {
            return allocationByOccurrence.get(slot.occurrenceId());
        }

        boolean isVisible(OccurrenceSlotDto slot) {
            Allocation allocation = allocationOf(slot);
            if (allocation != null) {
                return inScopeOrUnknown(allocation.getClassroomId());
            }
            return slot.status() == OccurrenceStatus.NEEDS_ROOM || inScopeOrUnknown(slot.previousClassroomId());
        }

        OccurrenceBoardSlotDto toSlot(OccurrenceSlotDto slot) {
            Allocation allocation = allocationOf(slot);
            return new OccurrenceBoardSlotDto(slot.occurrenceId(), slot.roomSlot(), slot.status(),
                    slot.releaseReason(), slot.releaseNote(),
                    allocation == null ? null : toAllocation(allocation),
                    allocation == null ? previousClassroom(slot.previousClassroomId()) : null);
        }

        private boolean inScopeOrUnknown(Long classroomId) {
            ClassroomResponseDto classroom = classroomById.get(classroomId);
            return classroom == null || scope.allows(classroom.buildingId());
        }

        private OccurrenceBoardAllocationDto toAllocation(Allocation allocation) {
            ClassroomResponseDto c = classroomById.get(allocation.getClassroomId());
            if (c == null) {
                return new OccurrenceBoardAllocationDto(allocation.getId(), allocation.getClassroomId(),
                        null, null, null, null);
            }
            return new OccurrenceBoardAllocationDto(allocation.getId(), c.id(), c.roomNumber(), c.buildingId(),
                    c.buildingName(), c.capacity());
        }

        private OccurrenceBoardClassroomDto previousClassroom(Long classroomId) {
            ClassroomResponseDto c = classroomById.get(classroomId);
            if (c == null || !scope.allows(c.buildingId())) {
                return null;
            }
            return new OccurrenceBoardClassroomDto(c.id(), c.roomNumber(), c.buildingId(), c.buildingName(),
                    c.capacity(), c.unavailableReason());
        }
    }
}
