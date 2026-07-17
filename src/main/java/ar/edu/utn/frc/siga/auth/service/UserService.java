package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.dto.request.CreateUserRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Administración de usuarios. Las operaciones de alta, habilitación y cambio de rol son
 * exclusivas de SUBSECRETARIA (autorización aplicada en el controller vía {@code @PreAuthorize}).
 */
public interface UserService {

    /** Da de alta un usuario habilitado con la contraseña hasheada. */
    UserResponseDto create(CreateUserRequestDto dto);

    /** Habilita o inhabilita un usuario; al inhabilitar revoca sus refresh tokens. */
    UserResponseDto setEnabled(Integer id, boolean enabled);

    /**
     * Cambia el rol de un usuario y revoca sus refresh tokens para forzar la re-emisión.
     * {@code currentUserEmail} es el email del usuario autenticado: no puede editar su propio rol.
     * {@code rol} debe coincidir con el nombre de una constante de {@link ar.edu.utn.frc.siga.auth.model.Role}.
     */
    UserResponseDto changeRole(Integer id, String rol, String currentUserEmail);

    /** Listado paginado (tamaño fijo 15) de usuarios habilitados. */
    Page<UserResponseDto> findEnabled(Pageable pageable);

    /** Listado paginado (tamaño fijo 15) de usuarios inhabilitados. */
    Page<UserResponseDto> findDisabled(Pageable pageable);
}
