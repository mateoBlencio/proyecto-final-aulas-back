package ar.edu.utn.frc.siga.audit.dto.response;

public enum AuditLogEntryType {

    /** Cambio individual sobre un registro (una revisión de Envers). */
    CHANGE,

    /** Operación de negocio que agrupa varios cambios; sus items se consultan con drill-down. */
    OPERATION
}
