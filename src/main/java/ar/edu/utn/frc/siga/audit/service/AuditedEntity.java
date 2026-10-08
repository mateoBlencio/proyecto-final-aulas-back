package ar.edu.utn.frc.siga.audit.service;

import java.util.List;

/**
 * Audited root entity. {@code auditTable} and {@code idColumn} are the Envers {@code _aud} table
 * and the id column of the original entity, used by the log's SQL reads. {@code columns} are the
 * audited properties, including those of JOINED subclasses (which live in their own {@code _aud} table).
 */
public record AuditedEntity(
        Class<?> javaType,
        String jpaName,
        String label,
        String auditTable,
        String idColumn,
        Class<?> idType,
        List<AuditedColumn> columns) {

    /** Audited column of {@code property}, living in {@code auditTable} (the root's or a subclass's). */
    public record AuditedColumn(String auditTable, String column, String property) {
    }
}
