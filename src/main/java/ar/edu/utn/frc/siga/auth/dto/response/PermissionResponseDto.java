package ar.edu.utn.frc.siga.auth.dto.response;

import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.security.ScopeType;

public record PermissionResponseDto(
        Permission permission,
        ScopeType scopeType) {
}
