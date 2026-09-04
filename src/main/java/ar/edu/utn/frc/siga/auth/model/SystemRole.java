package ar.edu.utn.frc.siga.auth.model;

import ar.edu.utn.frc.siga.common.security.Permission;
import java.util.EnumSet;
import java.util.Set;

public enum SystemRole {

    SUBSECRETARIA(EnumSet.allOf(Permission.class)),

    AUXILIAR_AULICO(EnumSet.of(
            Permission.BUILDING_READ,
            Permission.CLASSROOM_READ,
            Permission.CLASSROOM_CREATE,
            Permission.CLASSROOM_UPDATE,
            Permission.CLASSROOM_DELETE,
            Permission.CLASSROOM_ACTIVATE,
            Permission.CLASSROOM_TYPE_READ,
            Permission.RESOURCE_TYPE_READ,
            Permission.ACADEMIC_READ,
            Permission.EVENT_READ,
            Permission.ALLOCATION_READ,
            Permission.ALLOCATION_WRITE,
            Permission.CONFLICT_READ,
            Permission.PREVIEW_RUN,
            Permission.ROOM_REQUEST_READ,
            Permission.USER_READ)),

    CONSULTA(EnumSet.of(
            Permission.BUILDING_READ,
            Permission.CLASSROOM_READ,
            Permission.CLASSROOM_TYPE_READ,
            Permission.RESOURCE_TYPE_READ,
            Permission.ACADEMIC_READ,
            Permission.EVENT_READ,
            Permission.ALLOCATION_READ,
            Permission.CONFLICT_READ,
            Permission.ROOM_REQUEST_READ));

    private final Set<Permission> defaultPermissions;

    SystemRole(Set<Permission> defaultPermissions) {
        this.defaultPermissions = defaultPermissions;
    }

    public Set<Permission> defaultPermissions() {
        return defaultPermissions;
    }
}
