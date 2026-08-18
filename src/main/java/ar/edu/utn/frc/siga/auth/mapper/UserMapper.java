package ar.edu.utn.frc.siga.auth.mapper;

import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {

    @Mapping(target = "rol", source = "roles")
    UserResponseDto toDto(User user);

    default String firstRole(Set<Role> roles) {
        return (roles == null || roles.isEmpty()) ? null : roles.iterator().next().name();
    }
}
