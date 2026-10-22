package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class, uses = SpaceActionFlags.class)
public interface ClassroomMapper {

    @Mapping(target = "buildingId", source = "building.id")
    @Mapping(target = "buildingName", source = "building.name")
    @Mapping(target = "classroomTypeId", source = "classroomType.id")
    @Mapping(target = "classroomTypeDescription", source = "classroomType.description")
    @Mapping(target = "unavailableReason", expression = "java(entity.unavailableReason())")
    @Mapping(target = "currentInSysacad", expression = "java(entity.isCurrentInSysacad())")
    @Mapping(target = "canUpdate", source = ".", qualifiedByName = "canUpdateClassroom")
    @Mapping(target = "canActivate", source = "building.id", qualifiedByName = "canActivateClassroom")
    @Mapping(target = "canAllocate", source = ".", qualifiedByName = "canAllocateClassroom")
    ClassroomResponseDto toDto(Classroom entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "building", ignore = true)
    @Mapping(target = "classroomType", ignore = true)
    @Mapping(target = "syncedAt", ignore = true)
    @Mapping(target = "sysacadHash", ignore = true)
    @Mapping(target = "sysacadEnabled", ignore = true)
    @Mapping(target = "sysacadCurrent", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "permissionMode", ignore = true)
    @Mapping(target = "observations", ignore = true)
    Classroom toEntity(ClassroomRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "building", ignore = true)
    @Mapping(target = "classroomType", ignore = true)
    @Mapping(target = "syncedAt", ignore = true)
    @Mapping(target = "sysacadHash", ignore = true)
    @Mapping(target = "sysacadEnabled", ignore = true)
    @Mapping(target = "sysacadCurrent", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "permissionMode", ignore = true)
    @Mapping(target = "observations", ignore = true)
    void updateEntity(@MappingTarget Classroom entity, ClassroomRequestDto dto);
}
