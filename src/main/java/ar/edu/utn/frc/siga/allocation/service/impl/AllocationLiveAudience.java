package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.common.realtime.LiveTopicAudience;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ven los cambios de asignaciones quienes leen la grilla, el tablero de Eventos o el dashboard de ese edificio. */
@Component
@RequiredArgsConstructor
class AllocationLiveAudience implements LiveTopicAudience {

    private final BuildingScopeResolver buildingScopeResolver;

    @Override
    public LiveTopic topic() {
        return LiveTopic.ALLOCATIONS;
    }

    @Override
    public BuildingScope currentScope() {
        return buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)
                .union(buildingScopeResolver.scopeFor(Permission.CONFLICT_READ))
                .union(buildingScopeResolver.scopeFor(Permission.DASHBOARD_READ));
    }
}
