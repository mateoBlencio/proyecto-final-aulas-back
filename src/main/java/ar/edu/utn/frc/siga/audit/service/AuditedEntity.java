package ar.edu.utn.frc.siga.audit.service;

/**
 * Audited root entity. {@code auditTable} and {@code idColumn} are the Envers {@code _aud} table
 * and the id column of the original entity, used by the log's SQL reads.
 */
public record AuditedEntity(
        Class<?> javaType,
        String jpaName,
        String label,
        String auditTable,
        String idColumn) {
}
