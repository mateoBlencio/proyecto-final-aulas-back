package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;

/** Mapea {@link Specialty} hacia su DTO. */
@Mapper(config = CentralMapperConfig.class)
public interface SpecialtyMapper {

    SpecialtyResponseDto toDto(Specialty specialty);
}
