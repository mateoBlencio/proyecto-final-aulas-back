package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import org.mapstruct.Mapper;

/**
 * Antes el mapeo de {@link Building} a {@link BuildingResponseDto} vivía inline en
 * {@code BuildingServiceImpl.findAll()}. Se extrae a mapper para seguir la convención
 * única toDto/toEntity/updateEntity del resto de los agregados.
 */
@Mapper(config = CentralMapperConfig.class)
public interface BuildingMapper {

    BuildingResponseDto toDto(Building building);
}
