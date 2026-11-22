package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

final class DeactivatedClassroomReleaserFixture {

    static final LocalDate FUTURE = LocalDate.now().plusDays(10);

    private final Map<Long, OccurrenceSlotDto> slots = new LinkedHashMap<>();
    private final List<Allocation> allocations = new ArrayList<>();
    private final Map<Long, Integer> capacityByClassroom = new LinkedHashMap<>();
    private long nextAllocationId = 1000L;

    OccurrenceSlotDto principal(long id, LocalDate date, LocalTime start) {
        return add(new OccurrenceSlotDto(id, 1L, date, start, start.plusHours(2), OccurrenceStatus.NEEDS_ROOM, 90, 1,
                null, null, null, null));
    }

    OccurrenceSlotDto principal(long id) {
        return principal(id, FUTURE, LocalTime.of(8, 0));
    }

    OccurrenceSlotDto mirror(long id, OccurrenceSlotDto principal, int roomSlot) {
        return add(new OccurrenceSlotDto(id, principal.eventId(), principal.date(), principal.startTime(),
                principal.endTime(), OccurrenceStatus.NEEDS_ROOM, principal.enrolled(), roomSlot, null, null, null,
                principal.occurrenceId()));
    }

    OccurrenceSlotDto retiredMirror(long id, OccurrenceSlotDto principal, int roomSlot) {
        return add(new OccurrenceSlotDto(id, principal.eventId(), principal.date(), principal.startTime(),
                principal.endTime(), OccurrenceStatus.ROOM_RELEASED, principal.enrolled(), roomSlot,
                ReleaseReason.MIRROR_RETIRED, "Aula deshabilitada", null, principal.occurrenceId()));
    }

    Allocation allocate(OccurrenceSlotDto occurrence, long classroomId) {
        return allocate(occurrence, classroomId, AllocationSource.MANUAL);
    }

    Allocation allocate(OccurrenceSlotDto occurrence, long classroomId, AllocationSource source) {
        Allocation allocation = Allocation.builder().id(nextAllocationId++).occurrenceId(occurrence.occurrenceId())
                .classroomId(classroomId).source(source).build();
        allocations.add(allocation);
        return allocation;
    }

    void capacity(long classroomId, int capacity) {
        capacityByClassroom.put(classroomId, capacity);
    }

    void stub(AllocationRepository allocationRepository, OccurrenceService occurrenceService,
            ClassroomService classroomService) {
        lenient().when(allocationRepository.findByClassroomIdInFromDate(anyCollection(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Collection<Long> classrooms = inv.getArgument(0);
                    LocalDate from = inv.getArgument(1);
                    return allocations.stream()
                            .filter(a -> classrooms.contains(a.getClassroomId()))
                            .filter(a -> !slots.get(a.getOccurrenceId()).date().isBefore(from))
                            .toList();
                });
        lenient().when(allocationRepository.findByOccurrenceIdIn(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return allocations.stream().filter(a -> ids.contains(a.getOccurrenceId())).toList();
        });
        lenient().when(occurrenceService.findSlots(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return slots.values().stream().filter(s -> ids.contains(s.occurrenceId())).toList();
        });
        lenient().when(occurrenceService.findMirrors(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return slots.values().stream()
                    .filter(s -> s.mirrorOfOccurrenceId() != null && ids.contains(s.mirrorOfOccurrenceId()))
                    .toList();
        });
        lenient().when(classroomService.findByIdsIncludingDeactivated(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream()
                    .map(id -> new ClassroomResponseDto(id, id.intValue(), capacityByClassroom.getOrDefault(id, 40),
                            1L, "Central", 1L, "Aula", null, true))
                    .toList();
        });
    }

    private OccurrenceSlotDto add(OccurrenceSlotDto slot) {
        slots.put(slot.occurrenceId(), slot);
        return slot;
    }
}
