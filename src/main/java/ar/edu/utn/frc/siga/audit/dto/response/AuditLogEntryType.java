package ar.edu.utn.frc.siga.audit.dto.response;

public enum AuditLogEntryType {

    /** Cambio individual sobre un registro (una revisión de Envers). */
    CHANGE,

    /** Operación de negocio que agrupa varios cambios; sus items se consultan con drill-down. */
    OPERATION,

    /**
     * Transaction without a business operation that touched more than one record (one Envers revision);
     * its items are fetched with {@code GET /v1/audit/revisions/{revision}}.
     */
    TRANSACTION
}
