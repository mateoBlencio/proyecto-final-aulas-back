package ar.edu.utn.frc.siga.space.mapper;

import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Calcula las pistas de UI (can*) con el alcance del usuario autenticado y el estado del recurso, igual que el
 * enforcement: editar exige aula activa, crear exige edificio activo, asignar exige aula disponible. Activar/desactivar
 * depende solo del alcance. El enforcement sigue en cada endpoint.
 */
@Component
@RequiredArgsConstructor
public class SpaceActionFlags {

    private final BuildingScopeResolver scopeResolver;

    /** Alcance del usuario autenticado. Denegado bajo SystemScope o sin SecurityContext. */
    public BuildingScope userScope(Permission permission) {
        return SystemScope.isActive() ? BuildingScope.denied() : scopeResolver.scopeFor(permission);
    }

    @Named("canUpdateClassroom")
    public boolean canUpdateClassroom(Classroom classroom) {
        return classroom.isActive() && userScope(Permission.CLASSROOM_UPDATE).allows(classroom.getBuilding().getId());
    }

    @Named("canActivateClassroom")
    public boolean canActivateClassroom(Long buildingId) {
        return userScope(Permission.CLASSROOM_ACTIVATE).allows(buildingId);
    }

    @Named("canAllocateClassroom")
    public boolean canAllocateClassroom(Classroom classroom) {
        return classroom.isAvailable() && userScope(Permission.ALLOCATION_WRITE).allows(classroom.getBuilding().getId());
    }

    @Named("canCreateClassroom")
    public boolean canCreateClassroom(Building building) {
        return building.isActive() && userScope(Permission.CLASSROOM_CREATE).allows(building.getId());
    }

    /** BUILDING_ACTIVATE es global; el id solo existe para que MapStruct pueda enlazar {@code source = "id"}. */
    @Named("canActivateBuilding")
    public boolean canActivateBuilding(Long buildingId) {
        return userScope(Permission.BUILDING_ACTIVATE).isUnrestricted();
    }
}
