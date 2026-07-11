package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;

/** Mapea {@link Commission} hacia su DTO, incluyendo el período académico. */
@Mapper(config = CentralMapperConfig.class, uses = AcademicPeriodMapper.class)
public interface CommissionMapper {

    CommissionResponseDto toDto(Commission commission);
}
