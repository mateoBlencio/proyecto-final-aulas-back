package ar.edu.utn.frc.siga.common.security;

public enum Permission {

    BUILDING_READ(ScopeType.BUILDING),
    BUILDING_ACTIVATE(ScopeType.GLOBAL),
    CLASSROOM_READ(ScopeType.BUILDING),
    CLASSROOM_CREATE(ScopeType.BUILDING),
    CLASSROOM_UPDATE(ScopeType.BUILDING),
    CLASSROOM_DELETE(ScopeType.BUILDING),
    CLASSROOM_ACTIVATE(ScopeType.BUILDING),
    CLASSROOM_TYPE_READ(ScopeType.GLOBAL),
    CLASSROOM_TYPE_MANAGE(ScopeType.GLOBAL),
    RESOURCE_TYPE_READ(ScopeType.GLOBAL),
    RESOURCE_TYPE_MANAGE(ScopeType.GLOBAL),

    ACADEMIC_READ(ScopeType.GLOBAL),
    ACADEMIC_ACTIVATE(ScopeType.GLOBAL),

    EVENT_READ(ScopeType.GLOBAL),
    EVENT_MANAGE(ScopeType.GLOBAL),
    OCCURRENCE_RELEASE(ScopeType.GLOBAL),
    OCCURRENCE_REQUEST_ROOM(ScopeType.GLOBAL),

    ALLOCATION_READ(ScopeType.BUILDING),
    ALLOCATION_WRITE(ScopeType.BUILDING),
    CONFLICT_READ(ScopeType.BUILDING),
    ALLOCATION_HISTORY_READ(ScopeType.GLOBAL),

    PREVIEW_RUN(ScopeType.BUILDING),

    ROOM_REQUEST_READ(ScopeType.GLOBAL),

    SETTINGS_READ(ScopeType.GLOBAL),
    SETTINGS_WRITE(ScopeType.GLOBAL),
    INGEST_RUN(ScopeType.GLOBAL),
    SYSACAD_READ(ScopeType.GLOBAL),
    SYSACAD_SYNC(ScopeType.GLOBAL),
    USER_READ(ScopeType.GLOBAL),
    USER_MANAGE(ScopeType.GLOBAL),
    ROLE_ASSIGN(ScopeType.GLOBAL),
    ROLE_MANAGE(ScopeType.GLOBAL);

    private final ScopeType scopeType;

    Permission(ScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public ScopeType scopeType() {
        return scopeType;
    }
}
