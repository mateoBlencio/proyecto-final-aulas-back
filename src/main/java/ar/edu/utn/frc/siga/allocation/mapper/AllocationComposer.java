package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
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

@Component
@RequiredArgsConstructor
public class AllocationComposer {

    private final AllocationMapper mapper;
    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final ClassroomService classroomService;

    public AllocationResponseDto compose(Allocation allocation) {
        return composeAll(List.of(allocation)).getFirst();
    }

    public List<AllocationResponseDto> composeAll(List<Allocation> allocations) {
        Set<Long> occurrenceIds = allocations.stream()
                .map(Allocation::getOccurrenceId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, OccurrenceSlotDto> slotByOccurrenceId = Maps.byId(
                occurrenceService.findSlots(occurrenceIds), OccurrenceSlotDto::occurrenceId);

        Set<Long> eventIds = slotByOccurrenceId.values().stream()
                .map(OccurrenceSlotDto::eventId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, AcademicEventResponseDto> eventDtoById = Maps.byId(
                academicEventService.findByIds(eventIds), AcademicEventResponseDto::id);

        Set<Long> classroomIds = allocations.stream()
                .map(Allocation::getClassroomId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ClassroomResponseDto> classroomsById = Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);

        List<AllocationResponseDto> result = new ArrayList<>(allocations.size());
        for (Allocation allocation : allocations) {
            OccurrenceSlotDto slot = slotByOccurrenceId.get(allocation.getOccurrenceId());
            OccurrenceResponseDto occurrence = new OccurrenceResponseDto(
                    slot.occurrenceId(), slot.eventId(), slot.date(), slot.status(), slot.startTime(), slot.endTime());
            AcademicEventResponseDto event = eventDtoById.get(slot.eventId());
            ClassroomResponseDto classroom = classroomsById.get(allocation.getClassroomId());
            result.add(mapper.toDto(allocation, occurrence, event, classroom));
        }
        return result;
    }
}
