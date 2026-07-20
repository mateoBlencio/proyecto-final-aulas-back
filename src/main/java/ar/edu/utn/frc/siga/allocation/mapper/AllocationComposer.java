package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
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

/** Compone el DTO de una asignación resolviendo evento académico y aula, datos ajenos a la entidad. */
@Component
@RequiredArgsConstructor
public class AllocationComposer {

    private final AllocationMapper mapper;
    private final AcademicEventComposer eventComposer;
    private final ClassroomService classroomService;

    /** Composición de una única asignación (delega en el batch con una lista de un elemento). */
    public AllocationResponseDto compose(Allocation allocation) {
        return composeAll(List.of(allocation)).get(0);
    }

    /**
     * Composición por lote: prefetch de aulas distintas en un solo batch, sin N+1.
     * Tolerante a aulas inexistentes/borradas (asignaciones históricas): en ese caso el
     * aula viaja {@code null} en el DTO en vez de lanzar 404.
     */
    public List<AllocationResponseDto> composeAll(List<Allocation> allocations) {
        List<AcademicEventResponseDto> events = eventComposer.compose(
                allocations.stream().map(a -> a.getOccurrence().getEvent()).toList());

        Set<Integer> classroomIds = allocations.stream()
                .map(Allocation::getClassroomId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, ClassroomResponseDto> classroomsById = Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);

        List<AllocationResponseDto> result = new ArrayList<>(allocations.size());
        for (int i = 0; i < allocations.size(); i++) {
            Allocation allocation = allocations.get(i);
            ClassroomResponseDto classroom = classroomsById.get(allocation.getClassroomId());
            result.add(mapper.toDto(allocation, events.get(i), classroom));
        }
        return result;
    }
}
