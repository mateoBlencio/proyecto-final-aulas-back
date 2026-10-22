package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class, uses = AcademicPeriodMapper.class)
public interface CommissionMapper {

    @Mapping(target = "unavailableReason", expression = "java(commission.unavailableReason())")
    @Mapping(target = "specialties", ignore = true)
    CommissionResponseDto toDto(Commission commission);
}
