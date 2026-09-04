package ar.edu.utn.frc.siga.common.security;

import java.util.Collection;
import org.springframework.security.access.AccessDeniedException;

public interface BuildingScopeResolver {

    BuildingScope scopeFor(Permission permission);

    boolean canAccess(Permission permission, Long buildingId);

    void requireAccess(Permission permission, Long buildingId);

    void requireAccess(Permission permission, Collection<Long> buildingIds);
}
