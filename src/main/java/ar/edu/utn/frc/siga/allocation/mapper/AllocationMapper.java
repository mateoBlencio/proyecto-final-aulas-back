package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface AllocationMapper {

    @Mapping(target = "id", source = "allocation.id")
    @Mapping(target = "occurrence", source = "occurrence")
    @Mapping(target = "event", source = "event")
    @Mapping(target = "classroom", source = "classroom")
    AllocationResponseDto toDto(Allocation allocation, OccurrenceResponseDto occurrence,
            AcademicEventResponseDto event, ClassroomResponseDto classroom);
}
