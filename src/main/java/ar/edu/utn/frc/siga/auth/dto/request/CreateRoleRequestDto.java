package ar.edu.utn.frc.siga.auth.dto.request;

import ar.edu.utn.frc.siga.common.security.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record CreateRoleRequestDto(
        @NotBlank String name,
        @NotEmpty Set<Permission> permissions) {
}
