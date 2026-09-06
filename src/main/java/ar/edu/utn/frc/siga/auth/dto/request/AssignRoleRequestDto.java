package ar.edu.utn.frc.siga.auth.dto.request;

import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequestDto(
        @NotNull SystemRole role,
        @NotNull ScopeType scopeType,
        Long scopeId) {
}
