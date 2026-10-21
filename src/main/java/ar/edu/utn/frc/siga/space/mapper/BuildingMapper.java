package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class, uses = SpaceActionFlags.class)
public interface BuildingMapper {

    @Mapping(target = "active", expression = "java(building.isActive())")
    @Mapping(target = "unavailableReason", expression = "java(building.unavailableReason())")
    @Mapping(target = "canActivate", source = "id", qualifiedByName = "canActivateBuilding")
    @Mapping(target = "canCreateClassroom", source = ".", qualifiedByName = "canCreateClassroom")
    BuildingResponseDto toDto(Building building);
}
