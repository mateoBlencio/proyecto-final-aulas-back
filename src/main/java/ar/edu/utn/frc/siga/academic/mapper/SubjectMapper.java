package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;

/** Mapea {@link Subject} hacia su DTO, incluyendo el plan de estudio. */
@Mapper(config = CentralMapperConfig.class, uses = StudyPlanMapper.class)
public interface SubjectMapper {

    SubjectResponseDto toDto(Subject subject);
}
