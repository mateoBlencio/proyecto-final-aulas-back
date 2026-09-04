package ar.edu.utn.frc.siga.auth.dto.response;

import ar.edu.utn.frc.siga.common.security.ScopeType;

public record RoleAssignmentDto(
        Long id,
        String roleName,
        ScopeType scopeType,
        Long scopeId,
        String scopeName) {
}
