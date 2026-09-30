package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class, uses = SpecialtyMapper.class)
public interface StudyPlanMapper {

    @Mapping(target = "enabled", expression = "java(studyPlan.isActive())")
    StudyPlanResponseDto toDto(StudyPlan studyPlan);
}
