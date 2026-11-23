package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.common.realtime.LiveTopicAudience;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/** Misma regla que la bandeja: Subsecretaría ve todo y un auxiliar solo lo derivado a sus edificios. */
@Component
@RequiredArgsConstructor
class RoomRequestLiveAudience implements LiveTopicAudience {

    private final BuildingScopeResolver buildingScopeResolver;
    private final RoomRequestAccessControl accessControl;

    @Override
    public LiveTopic topic() {
        return LiveTopic.ROOM_REQUESTS;
    }

    @Override
    public BuildingScope currentScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !buildingScopeResolver.scopeFor(Permission.ROOM_REQUEST_READ).isUnrestricted()) {
            return BuildingScope.denied();
        }
        Optional<Set<Long>> readScope = accessControl.readScope(authentication.getName());
        return readScope.map(BuildingScope::of).orElseGet(BuildingScope::unrestricted);
    }
}
