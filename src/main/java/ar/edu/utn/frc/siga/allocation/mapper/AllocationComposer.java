package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationGroupDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSiblingDto;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AllocationComposer {

    private final AllocationMapper mapper;
    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final ClassroomService classroomService;
    private final AllocationRepository allocationRepository;

    public AllocationResponseDto compose(Allocation allocation) {
        return composeAll(List.of(allocation)).getFirst();
    }

    public List<AllocationResponseDto> composeAll(List<Allocation> allocations) {
        if (allocations.isEmpty()) {
            return List.of();
        }
        Set<Long> occurrenceIds = allocations.stream()
                .map(Allocation::getOccurrenceId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, OccurrenceSlotDto> slotByOccurrenceId = Maps.byId(
                occurrenceService.findSlots(occurrenceIds), OccurrenceSlotDto::occurrenceId);

        Set<Long> eventIds = slotByOccurrenceId.values().stream()
                .map(OccurrenceSlotDto::eventId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, AcademicEventResponseDto> eventDtoById = Maps.byId(
                academicEventService.findByIds(eventIds), AcademicEventResponseDto::id);

        Map<GroupKey, List<Allocation>> groups = groupByEventAndDate(allocations, slotByOccurrenceId);

        Set<Long> classroomIds = groups.values().stream().flatMap(List::stream)
                .map(Allocation::getClassroomId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ClassroomResponseDto> classroomsById = Maps.byId(classroomService.findByIdsIncludingDeactivated(classroomIds), ClassroomResponseDto::id);

        List<AllocationResponseDto> result = new ArrayList<>(allocations.size());
        for (Allocation allocation : allocations) {
            OccurrenceSlotDto slot = slotByOccurrenceId.get(allocation.getOccurrenceId());
            OccurrenceResponseDto occurrence = new OccurrenceResponseDto(
                    slot.occurrenceId(), slot.eventId(), slot.date(), slot.status(), slot.startTime(), slot.endTime(),
                    slot.releaseReason(), slot.releaseNote(), slot.previousClassroomId());
            AcademicEventResponseDto event = eventDtoById.get(slot.eventId());
            ClassroomResponseDto classroom = classroomsById.get(allocation.getClassroomId());
            AllocationGroupDto group = groupOf(allocation, groups.get(new GroupKey(slot.eventId(), slot.date())),
                    classroomsById);
            result.add(mapper.toDto(allocation, occurrence, event, classroom, group));
        }
        return result;
    }

    /** Asignaciones de cada (evento, fecha) de las pedidas, incluidas las aulas hermanas que no vinieron en el lote. */
    private Map<GroupKey, List<Allocation>> groupByEventAndDate(List<Allocation> allocations,
            Map<Long, OccurrenceSlotDto> slotByOccurrenceId) {
        Set<GroupKey> keys = slotByOccurrenceId.values().stream()
                .map(slot -> new GroupKey(slot.eventId(), slot.date()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, GroupKey> keyByOccurrenceId = new LinkedHashMap<>();
        slotByOccurrenceId.values().forEach(slot -> keyByOccurrenceId.put(slot.occurrenceId(),
                new GroupKey(slot.eventId(), slot.date())));
        for (LocalDate date : keys.stream().map(GroupKey::date).collect(Collectors.toSet())) {
            for (OccurrenceSlotDto slot : occurrenceService.findSlotsByDate(date)) {
                GroupKey key = new GroupKey(slot.eventId(), slot.date());
                if (keys.contains(key) && slot.status() != OccurrenceStatus.ROOM_RELEASED) {
                    keyByOccurrenceId.putIfAbsent(slot.occurrenceId(), key);
                }
            }
        }

        Map<Long, Allocation> allocationById = new LinkedHashMap<>();
        allocations.forEach(a -> allocationById.put(a.getId(), a));
        allocationRepository.findByOccurrenceIdIn(keyByOccurrenceId.keySet())
                .forEach(a -> allocationById.putIfAbsent(a.getId(), a));

        Map<GroupKey, List<Allocation>> groups = new LinkedHashMap<>();
        for (Allocation a : allocationById.values()) {
            GroupKey key = keyByOccurrenceId.get(a.getOccurrenceId());
            if (key != null) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
            }
        }
        return groups;
    }

    private AllocationGroupDto groupOf(Allocation own, List<Allocation> members,
            Map<Long, ClassroomResponseDto> classroomsById) {
        boolean unknownCapacity = false;
        int capacity = 0;
        List<AllocationSiblingDto> siblings = new ArrayList<>();
        for (Allocation member : members) {
            ClassroomResponseDto classroom = classroomsById.get(member.getClassroomId());
            if (classroom == null || classroom.capacity() == null) {
                unknownCapacity = true;
            } else {
                capacity += classroom.capacity();
            }
            if (!member.getId().equals(own.getId()) && classroom != null) {
                siblings.add(new AllocationSiblingDto(member.getId(), classroom.id(), classroom.roomNumber(),
                        classroom.buildingId(), classroom.buildingName(), classroom.capacity()));
            }
        }
        return new AllocationGroupDto(members.size(), unknownCapacity ? null : capacity, siblings);
    }

    private record GroupKey(Long eventId, LocalDate date) {
    }
}
