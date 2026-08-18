package ar.edu.utn.frc.siga.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRoleRequestDto(
        @NotBlank String rol) {
}
