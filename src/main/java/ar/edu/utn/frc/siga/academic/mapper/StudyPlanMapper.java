package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;

/** Mapea {@link StudyPlan} hacia su DTO, incluyendo la especialidad. */
@Mapper(config = CentralMapperConfig.class, uses = SpecialtyMapper.class)
public interface StudyPlanMapper {

    StudyPlanResponseDto toDto(StudyPlan studyPlan);
}
