package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.dto.request.CreateUserRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponseDto create(CreateUserRequestDto dto, String currentUserEmail);

    UserResponseDto setEnabled(Long id, boolean enabled);

    Page<UserResponseDto> findEnabled(Pageable pageable);

    Page<UserResponseDto> findDisabled(Pageable pageable);

    List<UserResponseDto> findByRoleForBuilding(SystemRole role, Long buildingId);
}
