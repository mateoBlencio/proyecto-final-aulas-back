package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomTypeResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface ClassroomTypeMapper {

    @Mapping(target = "enabled", expression = "java(classroomType.isActive())")
    ClassroomTypeResponseDto toDto(ClassroomType classroomType);
}
