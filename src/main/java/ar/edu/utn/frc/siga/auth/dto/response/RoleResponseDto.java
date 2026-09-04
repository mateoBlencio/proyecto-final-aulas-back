package ar.edu.utn.frc.siga.auth.dto.response;

import ar.edu.utn.frc.siga.common.security.Permission;

import java.util.Set;

public record RoleResponseDto(
        Long id,
        String name,
        boolean systemRole,
        Set<Permission> permissions) {
}
