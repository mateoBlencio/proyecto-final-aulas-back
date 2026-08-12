package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import org.mapstruct.Mapper;

@Mapper(config = CentralMapperConfig.class)
public interface BuildingMapper {

    BuildingResponseDto toDto(Building building);
}
