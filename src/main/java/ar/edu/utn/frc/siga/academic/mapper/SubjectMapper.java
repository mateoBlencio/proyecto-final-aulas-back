package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class, uses = StudyPlanMapper.class)
public interface SubjectMapper {

    @Mapping(target = "enabled", expression = "java(subject.isActive())")
    SubjectResponseDto toDto(Subject subject);
}
