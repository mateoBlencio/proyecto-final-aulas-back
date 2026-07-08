package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.space.mapper.ClassroomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AllocationMapper {

    private final AcademicEventComposer composer;
    private final ClassroomMapper classroomMapper;

    public AllocationResponseDto toDto(Allocation allocation) {
        Occurrence occurrence = allocation.getOccurrence();

        OccurrenceResponseDto occurrenceDto = OccurrenceResponseDto.builder()
                .id(occurrence.getId())
                .eventId(occurrence.getEvent().getId())
                .date(occurrence.getDate())
                .status(occurrence.getStatus())
                .startTime(occurrence.startTime())
                .endTime(occurrence.endTime())
                .build();

        return AllocationResponseDto.builder()
                .id(allocation.getId())
                .source(allocation.getSource())
                .createdAt(allocation.getCreatedAt())
                .observation(allocation.getObservation())
                .occurrence(occurrenceDto)
                .event(composer.compose(occurrence.getEvent()))
                .classroom(classroomMapper.toResponseDto(allocation.getClassroom()))
                .build();
    }
}