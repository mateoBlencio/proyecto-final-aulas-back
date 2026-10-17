package ar.edu.utn.frc.siga.auth.model;

import ar.edu.utn.frc.siga.common.security.Permission;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
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
            Permission.EVENT_DIRECT_CREATE,
            Permission.OCCURRENCE_RELEASE,
            Permission.OCCURRENCE_REQUEST_ROOM,
            Permission.ALLOCATION_READ,
            Permission.ALLOCATION_WRITE,
            Permission.ALLOCATION_HISTORY_READ,
            Permission.BUILDING_ACTIVITY_READ,
            Permission.CONFLICT_READ,
            Permission.PREVIEW_RUN,
            Permission.ROOM_REQUEST_READ,
            Permission.ROOM_REQUEST_WRITE,
            Permission.DASHBOARD_READ,
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
            Permission.ROOM_REQUEST_READ,
            Permission.DASHBOARD_READ));

    private final Set<Permission> permissions;

    SystemRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
