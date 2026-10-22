package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface AcademicPeriodMapper {

    @Mapping(target = "enabled", expression = "java(period.isActive())")
    @Mapping(target = "unavailableReason", expression = "java(period.isActive() ? null : ar.edu.utn.frc.siga.common.model.UnavailableReason.DEACTIVATED)")
    AcademicPeriodResponseDto toDto(AcademicPeriod period);
}
