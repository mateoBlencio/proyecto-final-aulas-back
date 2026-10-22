package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface SpecialtyMapper {

    @Mapping(target = "enabled", expression = "java(specialty.isActive())")
    @Mapping(target = "unavailableReason", expression = "java(specialty.unavailableReason())")
    @Mapping(target = "commissionCount", ignore = true)
    @Mapping(target = "subjectCount", ignore = true)
    SpecialtyResponseDto toDto(Specialty specialty);
}
