package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.response.ResourceTypeResponseDto;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface ResourceTypeMapper {

    @Mapping(target = "enabled", expression = "java(resourceType.isActive())")
    ResourceTypeResponseDto toDto(ResourceType resourceType);
}
