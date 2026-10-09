package ar.edu.utn.frc.siga.audit;

import java.util.Set;

/**
 * Single place that names the Flyway migration creating {@code revinfo_resumen}. If the migrations are
 * renumbered, only {@link #VERSION} changes.
 */
final class RevisionSummaryMigration {

    /** Flyway version of the migration that creates and backfills {@code revinfo_resumen}. */
    static final int VERSION = 17;

    /** Classpath pattern that resolves the migration file whatever its description. */
    static final String RESOURCE_PATTERN = "classpath:db/migration/V" + VERSION + "__*.sql";

    /** Audited tables born after the V17 backfill, so its SQL does not name them. */
    static final Set<String> TABLES_CREATED_LATER = Set.of("archivado_auditoria_aud");

    private RevisionSummaryMigration() {
    }
}
