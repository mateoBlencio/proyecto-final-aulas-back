package ar.edu.utn.frc.siga.auth.mapper;

import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapea {@link User} a su vista pública. El {@code Set<Role>} del entity se colapsa al primer
 * rol vigente porque la API modela un único rol por usuario. La construcción del entity y el
 * hash de la contraseña son lógica de negocio y viven en el service, no en el mapper.
 */
@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {

    @Mapping(target = "rol", source = "roles")
    UserResponseDto toDto(User user);

    default String firstRole(Set<Role> roles) {
        return (roles == null || roles.isEmpty()) ? null : roles.iterator().next().name();
    }
}
