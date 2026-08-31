package ar.edu.utn.frc.siga.space.permission;

import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PermissionTargetResolvers {

    private final Map<PermissionTargetKind, PermissionTargetResolver> byKind;

    public PermissionTargetResolvers(List<PermissionTargetResolver> resolvers) {
        this.byKind = resolvers.stream()
                .collect(Collectors.toMap(PermissionTargetResolver::kind, Function.identity()));
    }

    public Map<Long, String> resolveNames(PermissionTargetKind kind, Collection<Long> targetIds) {
        PermissionTargetResolver resolver = byKind.get(kind);
        if (resolver == null) {
            throw new IllegalStateException("No PermissionTargetResolver registered for kind " + kind);
        }
        return resolver.resolveNames(targetIds);
    }
}
