package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.dto.request.CreateUserRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface UserService {

    UserResponseDto create(CreateUserRequestDto dto, String currentUserEmail);

    UserResponseDto setEnabled(Long id, boolean enabled);

    Page<UserResponseDto> findEnabled(Pageable pageable);

    Page<UserResponseDto> findDisabled(Pageable pageable);

    List<UserResponseDto> findByRoleForBuilding(SystemRole role, Long buildingId);

    /** Subconjunto de buildingIds cubierto por algún usuario habilitado con ese rol (alcance GLOBAL o al edificio puntual). */
    Set<Long> findBuildingIdsCoveredByRole(SystemRole role, Collection<Long> buildingIds);
}
