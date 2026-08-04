package ar.edu.utn.frc.siga.common.dto.response;

/** Tipo de operación de una revisión de auditoría (mapea el {@code RevisionType} de Envers: ADD/MOD/DEL). */
public enum RevisionKind {
    CREATED,
    MODIFIED,
    DELETED
}
