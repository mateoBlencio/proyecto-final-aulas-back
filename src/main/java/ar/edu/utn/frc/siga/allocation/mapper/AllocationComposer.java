package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.mapper.ClassroomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Compone el DTO de una asignación resolviendo el evento académico (vía
 * {@link AcademicEventComposer}) y el aula (vía {@link ClassroomMapper}) — datos ajenos
 * al agregado {@link Allocation}. El fetch/mapeo de esos datos vive acá para que
 * {@link AllocationMapper} sea un mapper puro sobre la entidad, espejo del patrón
 * {@link AcademicEventComposer}.
 */
@Component
@RequiredArgsConstructor
public class AllocationComposer {

    private final AllocationMapper mapper;
    private final AcademicEventComposer eventComposer;
    private final ClassroomMapper classroomMapper;

    public AllocationResponseDto compose(Allocation allocation) {
        AcademicEventResponseDto event = eventComposer.compose(allocation.getOccurrence().getEvent());
        ClassroomResponseDto classroom = classroomMapper.toDto(allocation.getClassroom());
        return mapper.toDto(allocation, event, classroom);
    }
}
