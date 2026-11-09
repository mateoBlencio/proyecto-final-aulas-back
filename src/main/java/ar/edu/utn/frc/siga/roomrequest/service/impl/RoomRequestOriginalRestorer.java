package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.RestoreCommand;
import ar.edu.utn.frc.siga.allocation.service.command.RestoreCommand.RestoreItem;
import ar.edu.utn.frc.siga.allocation.service.command.RestoreResult;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.KeptDateDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OccupiedByDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OriginalConflictDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RevertedDateDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ReversionDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation.Original;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class RoomRequestOriginalRestorer {

    private final AllocationService allocationService;
    private final OccurrenceService occurrenceService;
    private final ClassroomService classroomService;
    private final RoomRequestOccurrenceResolver occurrenceResolver;

    /** {@code withoutOriginal}: fechas futuras sin aula original guardada ni aula anterior en el ítem. */
    record Restoration(List<Long> restored, List<OccurrenceConflictDto> occupied, List<Long> deactivated,
            List<Long> withoutOriginal, Map<Long, Long> swappedMirrors) {

        /** Fechas que al cancelar se liberan: no tenían aula original o esta quedó desactivada. */
        List<Long> toDeallocate() {
            return java.util.stream.Stream.concat(withoutOriginal.stream(), deactivated.stream()).toList();
        }
    }

    static boolean hasOriginalClassroom(RoomRequestType type) {
        return type == RoomRequestType.ONE_TIME_ROOM_CHANGE || type == RoomRequestType.REGULAR_ROOM_CHANGE
                || type == RoomRequestType.PARTIAL_EXAM_IN_CLASS;
    }

    Map<Long, Original> captureOriginals(Collection<Long> occurrenceIds) {
        Map<Long, Original> result = new LinkedHashMap<>();
        for (AllocationResponseDto allocation : allocationService.findByOccurrenceIds(occurrenceIds)) {
            if (allocation.classroom() != null && allocation.occurrence() != null) {
                result.put(allocation.occurrence().id(),
                        new Original(allocation.classroom().id(), allocation.source()));
            }
        }
        return result;
    }

    Restoration restore(RoomRequestItem item, String observation, boolean allOrNothing) {
        List<RoomRequestItemAllocation> principals = item.getAllocations().stream()
                .filter(a -> a.getPosition() == 1)
                .toList();
        Set<Long> futureIds = new HashSet<>(occurrenceResolver.onlyFuture(item,
                principals.stream().map(RoomRequestItemAllocation::getOccurrenceId).toList()));

        List<RestoreItem> items = new ArrayList<>();
        List<Long> withoutOriginal = new ArrayList<>();
        for (RoomRequestItemAllocation principal : principals) {
            if (!futureIds.contains(principal.getOccurrenceId())) {
                continue;
            }
            Long classroomId = item.originalClassroomOf(principal);
            if (classroomId == null) {
                withoutOriginal.add(principal.getOccurrenceId());
            } else {
                items.add(new RestoreItem(principal.getOccurrenceId(), classroomId, item.originalSourceOf(principal)));
            }
        }
        if (items.isEmpty()) {
            return new Restoration(List.of(), List.of(), List.of(), withoutOriginal, Map.of());
        }
        RestoreResult result = allocationService.restoreClassrooms(new RestoreCommand(items, observation, allOrNothing));
        return new Restoration(result.restored(), result.occupied(), result.deactivated(), withoutOriginal,
                result.swappedMirrors());
    }

    List<OriginalConflictDto> toConflicts(Restoration restoration) {
        Map<Long, OccupiedByDto> occupantByOccurrence = occupants(restoration.occupied());
        Map<Long, ClassroomOptionDto> classrooms = classroomOptions(
                restoration.occupied().stream().map(OccurrenceConflictDto::classroomId).toList());
        return restoration.occupied().stream()
                .sorted(Comparator.comparing(OccurrenceConflictDto::date))
                .map(c -> new OriginalConflictDto(c.date(), classrooms.get(c.classroomId()),
                        occupantByOccurrence.get(c.conflictingOccurrenceId())))
                .toList();
    }

    ReversionDto toReversion(RoomRequestItem item, Restoration restoration) {
        Set<Long> occurrenceIds = new LinkedHashSet<>(restoration.restored());
        restoration.occupied().forEach(c -> occurrenceIds.add(c.occurrenceId()));
        occurrenceIds.addAll(restoration.deactivated());
        occurrenceIds.addAll(restoration.withoutOriginal());
        Map<Long, OccurrenceSlotDto> slotById = occurrenceIds.isEmpty() ? Map.of()
                : Maps.byId(occurrenceService.findSlots(occurrenceIds), OccurrenceSlotDto::occurrenceId);

        Map<Long, Long> originalClassroomByOccurrence = new LinkedHashMap<>();
        item.getAllocations().stream().filter(a -> a.getPosition() == 1)
                .forEach(a -> originalClassroomByOccurrence.put(a.getOccurrenceId(), item.originalClassroomOf(a)));

        Map<Long, OccupiedByDto> occupantByOccurrence = occupants(restoration.occupied());
        Set<Long> classroomIds = new LinkedHashSet<>(originalClassroomByOccurrence.values());
        classroomIds.remove(null);
        Map<Long, ClassroomOptionDto> classrooms = classroomOptions(classroomIds);

        List<RevertedDateDto> reverted = restoration.restored().stream()
                .map(id -> new RevertedDateDto(slotById.get(id).date(),
                        classrooms.get(originalClassroomByOccurrence.get(id))))
                .sorted(Comparator.comparing(RevertedDateDto::date))
                .toList();

        List<KeptDateDto> kept = new ArrayList<>();
        for (OccurrenceConflictDto conflict : restoration.occupied()) {
            kept.add(new KeptDateDto(conflict.date(), classrooms.get(conflict.classroomId()),
                    occupantByOccurrence.get(conflict.conflictingOccurrenceId())));
        }
        for (Long id : restoration.deactivated()) {
            kept.add(new KeptDateDto(slotById.get(id).date(), classrooms.get(originalClassroomByOccurrence.get(id)), null));
        }
        for (Long id : restoration.withoutOriginal()) {
            kept.add(new KeptDateDto(slotById.get(id).date(), null, null));
        }
        kept.sort(Comparator.comparing(KeptDateDto::date));
        return new ReversionDto(reverted, kept);
    }

    private Map<Long, OccupiedByDto> occupants(List<OccurrenceConflictDto> conflicts) {
        Set<Long> occupantIds = conflicts.stream()
                .map(OccurrenceConflictDto::conflictingOccurrenceId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (occupantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, OccupiedByDto> result = new LinkedHashMap<>();
        for (AllocationResponseDto allocation : allocationService.findByOccurrenceIds(occupantIds)) {
            if (allocation.occurrence() != null && allocation.event() != null) {
                result.put(allocation.occurrence().id(), occupantOf(allocation.event()));
            }
        }
        return result;
    }

    private static OccupiedByDto occupantOf(AcademicEventResponseDto event) {
        String subjectName = event.subject() == null ? null : event.subject().name();
        String commissionCode = switch (event) {
            case RecurringEventResponseDto recurring ->
                    recurring.commission() == null ? null : recurring.commission().courseCode();
            case UniqueEventResponseDto unique ->
                    unique.commission() == null ? null : unique.commission().courseCode();
        };
        return new OccupiedByDto(subjectName, commissionCode);
    }

    private Map<Long, ClassroomOptionDto> classroomOptions(Collection<Long> classroomIds) {
        if (classroomIds.isEmpty()) {
            return Map.of();
        }
        return classroomService.findByIdsIncludingDeactivated(new HashSet<>(classroomIds)).stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id,
                        c -> new ClassroomOptionDto(c.id(), c.roomNumber(), c.buildingName())));
    }
}
