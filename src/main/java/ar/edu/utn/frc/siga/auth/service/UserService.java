package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.dto.request.CreateUserRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponseDto create(CreateUserRequestDto dto);

    UserResponseDto setEnabled(Integer id, boolean enabled);

    UserResponseDto changeRole(Integer id, String rol, String currentUserEmail);

    Page<UserResponseDto> findEnabled(Pageable pageable);

    Page<UserResponseDto> findDisabled(Pageable pageable);
}
