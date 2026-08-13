package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventAllocationResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.Overcrowding;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventAllocationComposer {

    private final OccurrenceService occurrenceService;
    private final AllocationRepository allocationRepository;
    private final ClassroomService classroomService;

    public UniqueEventAllocationResponseDto compose(UniqueEventResponseDto event, AllocationResponseDto allocation) {
        return new UniqueEventAllocationResponseDto(
                event,
                allocation.occurrence() != null ? allocation.occurrence().status() : null,
                allocation.classroom(),
                Overcrowding.by(event.enrolled(), allocation.classroom() != null ? allocation.classroom().capacity() : null),
                allocation.observation());
    }

    public List<UniqueEventAllocationResponseDto> composeAll(List<AcademicEventResponseDto> events) {
        List<UniqueEventResponseDto> uniqueEvents = events.stream().map(UniqueEventResponseDto.class::cast).toList();
        if (uniqueEvents.isEmpty()) {
            return List.of();
        }

        Set<Long> eventIds = uniqueEvents.stream().map(UniqueEventResponseDto::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, OccurrenceSlotDto> slotByEventId = Maps.byId(
                occurrenceService.findSlotsByEvents(eventIds), OccurrenceSlotDto::eventId);

        Set<Long> occurrenceIds = slotByEventId.values().stream().map(OccurrenceSlotDto::occurrenceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Allocation> allocationByOccurrenceId = Maps.byId(
                allocationRepository.findByOccurrenceIdIn(occurrenceIds), Allocation::getOccurrenceId);

        Set<Integer> classroomIds = allocationByOccurrenceId.values().stream().map(Allocation::getClassroomId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, ClassroomResponseDto> classroomById = Maps.byId(
                classroomService.findByIds(classroomIds), ClassroomResponseDto::id);

        List<UniqueEventAllocationResponseDto> result = new ArrayList<>(uniqueEvents.size());
        for (UniqueEventResponseDto event : uniqueEvents) {
            OccurrenceSlotDto slot = slotByEventId.get(event.id());
            Allocation allocation = slot != null ? allocationByOccurrenceId.get(slot.occurrenceId()) : null;
            ClassroomResponseDto classroom = allocation != null ? classroomById.get(allocation.getClassroomId()) : null;
            result.add(new UniqueEventAllocationResponseDto(
                    event,
                    slot != null ? slot.status() : null,
                    classroom,
                    Overcrowding.by(event.enrolled(), classroom != null ? classroom.capacity() : null),
                    allocation != null ? allocation.getObservation() : null));
        }
        return result;
    }
}
