package ar.edu.utn.frc.siga.auth.mapper;

import ar.edu.utn.frc.siga.auth.dto.response.RoleAssignmentDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {

    @Mapping(target = "roleAssignments", ignore = true)
    UserResponseDto toDto(User user);

    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "scopeName", ignore = true)
    RoleAssignmentDto toDto(RoleAssignment assignment);
}
