package ar.edu.utn.frc.siga.audit.repository;

/**
 * Audit tables moved to the {@code archivo} schema. {@code audit} cannot import the entities, so it names
 * the tables; {@link AuditArchiveRepository} checks them against the registry at startup.
 */
public enum ArchivedTable {

    OCCURRENCE("ocurrencia_aud", "id_ocurrencia"),
    ALLOCATION("asignacion_aula_aud", "id_asignacion");

    private final String table;
    private final String idColumn;

    ArchivedTable(String table, String idColumn) {
        this.table = table;
        this.idColumn = idColumn;
    }

    public String table() {
        return table;
    }

    public String idColumn() {
        return idColumn;
    }
}
