package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Mapea {@link Occurrence} a {@link OccurrenceResponseDto}. */
@Mapper(config = CentralMapperConfig.class)
public interface OccurrenceMapper {

    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "startTime", expression = "java(occurrence.startTime())")
    @Mapping(target = "endTime", expression = "java(occurrence.endTime())")
    OccurrenceResponseDto toDto(Occurrence occurrence);
}
