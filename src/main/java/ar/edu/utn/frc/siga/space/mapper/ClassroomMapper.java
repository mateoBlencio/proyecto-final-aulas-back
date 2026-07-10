package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
@Mapper(config = CentralMapperConfig.class)
public interface ClassroomMapper {

    @Mapping(target = "buildingId", source = "building.id")
    @Mapping(target = "buildingName", source = "building.name")
    @Mapping(target = "classroomTypeId", source = "classroomType.id")
    @Mapping(target = "classroomTypeDescription", source = "classroomType.description")
    ClassroomResponseDto toDto(Classroom entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "building", ignore = true)
    @Mapping(target = "classroomType", ignore = true)
    Classroom toEntity(ClassroomRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "building", ignore = true)
    @Mapping(target = "classroomType", ignore = true)
    void updateEntity(@MappingTarget Classroom entity, ClassroomRequestDto dto);
}
