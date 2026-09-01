package ar.edu.utn.frc.siga.space.permission;

import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import java.util.Collection;
import java.util.Map;

public interface PermissionTargetResolver {

    PermissionTargetKind kind();

    Map<Long, String> resolveNames(Collection<Long> targetIds);
}
