package ar.edu.utn.frc.siga.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Cambia el rol de un usuario. Una SUBSECRETARIA no puede editar su propio rol.
 */
public record UpdateUserRoleRequestDto(
        @NotBlank String rol) {
}
