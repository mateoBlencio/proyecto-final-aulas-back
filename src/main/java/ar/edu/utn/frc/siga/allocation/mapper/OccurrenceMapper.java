package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Antes el mapeo de {@link Occurrence} a {@link OccurrenceResponseDto} vivía inline
 * y duplicado en {@code AllocationMapper} y {@code AcademicEventServiceImpl}.
 */
@Mapper(config = CentralMapperConfig.class)
public interface OccurrenceMapper {

    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "startTime", expression = "java(occurrence.startTime())")
    @Mapping(target = "endTime", expression = "java(occurrence.endTime())")
    OccurrenceResponseDto toDto(Occurrence occurrence);
}
