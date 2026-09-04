package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import java.util.Collection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class DefaultBuildingScopeResolver implements BuildingScopeResolver {

    @Override
    public BuildingScope scopeFor(Permission permission) {
        SecurityUser principal = currentSecurityUser();
        return principal == null ? BuildingScope.denied() : principal.scopeFor(permission);
    }

    @Override
    public boolean canAccess(Permission permission, Long buildingId) {
        return scopeFor(permission).allows(buildingId);
    }

    @Override
    public void requireAccess(Permission permission, Long buildingId) {
        if (!canAccess(permission, buildingId)) {
            throw new AccessDeniedException(
                    "No tiene acceso al edificio " + buildingId + " para el permiso " + permission);
        }
    }

    @Override
    public void requireAccess(Permission permission, Collection<Long> buildingIds) {
        BuildingScope scope = scopeFor(permission);
        for (Long buildingId : buildingIds) {
            if (!scope.allows(buildingId)) {
                throw new AccessDeniedException(
                        "No tiene acceso al edificio " + buildingId + " para el permiso " + permission);
            }
        }
    }

    private SecurityUser currentSecurityUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            return null;
        }
        return securityUser;
    }
}
