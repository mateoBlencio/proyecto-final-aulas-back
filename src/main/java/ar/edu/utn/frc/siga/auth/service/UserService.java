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

    /** Si el usuario habilitado tiene ese rol asignado, con cualquier alcance. */
    boolean hasRole(String email, SystemRole role);

    /** Si el usuario habilitado tiene ese rol con alcance GLOBAL (cubre cualquier edificio). */
    boolean hasGlobalRole(String email, SystemRole role);

    /** Edificios a cargo del usuario para ese rol por alcance puntual; no incluye un eventual alcance GLOBAL, ver {@link #hasGlobalRole}. */
    Set<Long> findBuildingIdsForRole(String email, SystemRole role);
}
