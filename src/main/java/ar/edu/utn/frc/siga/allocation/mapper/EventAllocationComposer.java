package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventAllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.allocation.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.util.Maps;
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

/** Enriquece la vista de un {@code UniqueEvent} con aula/estado/sobrecupo: la parte que {@code events} no conoce. */
@Component
@RequiredArgsConstructor
public class EventAllocationComposer {

    private final OccurrenceService occurrenceService;
    private final AllocationRepository allocationRepository;
    private final ClassroomService classroomService;

    /** Composición desde una {@link AllocationResponseDto} ya resuelta (alta/modificación: la trae completa). */
    public UniqueEventAllocationResponseDto compose(UniqueEventResponseDto event, AllocationResponseDto allocation) {
        return new UniqueEventAllocationResponseDto(
                event,
                allocation.occurrence() != null ? allocation.occurrence().status() : null,
                allocation.classroom(),
                overcrowdedBy(event.enrolled(), allocation.classroom()),
                allocation.observation());
    }

    /** Composición por lote: prefetch de occurrence/allocation/aula, sin N+1. */
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
                    overcrowdedBy(event.enrolled(), classroom),
                    allocation != null ? allocation.getObservation() : null));
        }
        return result;
    }

    private Integer overcrowdedBy(Integer enrolled, ClassroomResponseDto classroom) {
        if (classroom == null || classroom.capacity() == null || enrolled == null) {
            return null;
        }
        return Math.max(0, enrolled - classroom.capacity());
    }
}
