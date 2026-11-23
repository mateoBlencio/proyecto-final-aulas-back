package ar.edu.utn.frc.siga.buildingactivity.service.impl;

import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.common.realtime.LiveTopicAudience;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BuildingActivityLiveAudience implements LiveTopicAudience {

    private final BuildingScopeResolver buildingScopeResolver;

    @Override
    public LiveTopic topic() {
        return LiveTopic.BUILDING_ACTIVITY;
    }

    @Override
    public BuildingScope currentScope() {
        return buildingScopeResolver.scopeFor(Permission.BUILDING_ACTIVITY_READ);
    }
}
