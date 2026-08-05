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

/** Compone el DTO de una asignación resolviendo occurrence, evento académico y aula, datos ajenos a la entidad. */
@Component
@RequiredArgsConstructor
public class AllocationComposer {

    private final AllocationMapper mapper;
    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final ClassroomService classroomService;

    /** Composición de una única asignación (delega en el batch con una lista de un elemento). */
    public AllocationResponseDto compose(Allocation allocation) {
        return composeAll(List.of(allocation)).getFirst();
    }

    /** Tolerante a aulas inexistentes/borradas (asignaciones históricas): viajan {@code null} en vez de lanzar 404. */
    public List<AllocationResponseDto> composeAll(List<Allocation> allocations) {
        Set<Long> occurrenceIds = allocations.stream()
                .map(Allocation::getOccurrenceId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, OccurrenceSlotDto> slotByOccurrenceId = Maps.byId(
                occurrenceService.findSlots(occurrenceIds), OccurrenceSlotDto::occurrenceId);

        Set<Long> eventIds = slotByOccurrenceId.values().stream()
                .map(OccurrenceSlotDto::eventId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, AcademicEventResponseDto> eventDtoById = Maps.byId(
                academicEventService.findByIds(eventIds), AcademicEventResponseDto::id);

        Set<Integer> classroomIds = allocations.stream()
                .map(Allocation::getClassroomId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, ClassroomResponseDto> classroomsById = Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);

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
