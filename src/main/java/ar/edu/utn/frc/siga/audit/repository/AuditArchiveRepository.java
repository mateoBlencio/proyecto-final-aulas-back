package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Moves old rows of {@code ocurrencia_aud} and {@code asignacion_aula_aud} (with their {@code revinfo} and
 * {@code revinfo_resumen}) to the {@code archivo} schema. Table and column names come from
 * {@link ArchivedTable} and {@link AuditedEntityRegistry}, never from input; only the cutoff is a parameter.
 * Copies use {@code SELECT a.*}: both schemas must keep the same column order (see V19).
 */
@Repository
@RequiredArgsConstructor
public class AuditArchiveRepository {

    private static final String LOCK_NAME = "siga.audit.archive";

    /**
     * Candidate revision {@code r}: older than the cutoff, and neither its operation nor its children
     * operations have a revision at or after it (an operation is never split; a parent stays while a child does).
     * With a null {@code operacion_id} both NOT EXISTS are true.
     */
    private static final String CANDIDATE = """
            r.fecha_revision < :cutoff
            AND NOT EXISTS (SELECT 1 FROM public.revinfo o
                             WHERE o.operacion_id = r.operacion_id AND o.fecha_revision >= :cutoff)
            AND NOT EXISTS (SELECT 1 FROM public.revinfo c
                             WHERE c.operacion_padre_id = r.operacion_id AND c.fecha_revision >= :cutoff)""";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditedEntityRegistry registry;

    private final Map<ArchivedTable, Statements> statements = new EnumMap<>(ArchivedTable.class);
    private String countPendingSql;

    /** Rows moved by one batch and the {@code revinfo} rows deleted from the active table. */
    public record ArchiveBatch(int rows, int deletedRevisions) {
    }

    private record Statements(String select, String copyResumen, String copyRows, String verifyCopy, String delete,
                              String discount, String deleteEmptyResumen, String deleteRevisions) {
    }

    @PostConstruct
    void buildStatements() {
        for (ArchivedTable table : ArchivedTable.values()) {
            boolean audited = registry.all().stream().anyMatch(entity ->
                    entity.auditTable().equals(table.table()) && entity.idColumn().equals(table.idColumn()));
            if (!audited) {
                throw new IllegalStateException("ArchivedTable." + table + " no coincide con ninguna entidad auditada ("
                        + table.table() + "." + table.idColumn() + ")");
            }
        }

        String noRowsLeft = registry.all().stream()
                .map(entity -> "AND NOT EXISTS (SELECT 1 FROM public." + entity.auditTable()
                        + " x WHERE x.rev = r.rev)")
                .collect(Collectors.joining("\n                "));
        for (ArchivedTable table : ArchivedTable.values()) {
            String name = table.table();
            String id = table.idColumn();
            statements.put(table, new Statements(
                    "INSERT INTO lote_archivo SELECT a.rev, a." + id + ", a.revtype FROM public." + name
                            + " a JOIN public.revinfo r ON r.rev = a.rev WHERE " + CANDIDATE
                            + " ORDER BY a.rev, a." + id + " LIMIT :batchSize",
                    "INSERT INTO archivo.revinfo_resumen SELECT s.* FROM public.revinfo_resumen s"
                            + " WHERE s.tabla_aud = '" + name + "' AND s.rev IN (SELECT rev FROM lote_archivo)"
                            + " ON CONFLICT DO NOTHING",
                    "INSERT INTO archivo." + name + " SELECT a.* FROM public." + name
                            + " a JOIN lote_archivo l ON l.rev = a.rev AND l.id = a." + id + " ON CONFLICT DO NOTHING",
                    "SELECT count(*) FROM (SELECT a.* FROM public." + name
                            + " a JOIN lote_archivo l ON l.rev = a.rev AND l.id = a." + id
                            + " EXCEPT SELECT * FROM archivo." + name + ") missing",
                    "DELETE FROM public." + name + " a USING lote_archivo l WHERE a.rev = l.rev AND a." + id + " = l.id",
                    "UPDATE public.revinfo_resumen s SET cantidad = s.cantidad - l.n FROM"
                            + " (SELECT rev, revtype, count(*) AS n FROM lote_archivo GROUP BY rev, revtype) l"
                            + " WHERE s.rev = l.rev AND s.revtype = l.revtype AND s.tabla_aud = '" + name + "'",
                    "DELETE FROM public.revinfo_resumen WHERE tabla_aud = '" + name
                            + "' AND rev IN (SELECT rev FROM lote_archivo) AND cantidad <= 0",
                    "DELETE FROM public.revinfo r WHERE r.rev IN (SELECT rev FROM lote_archivo)"
                            + " AND NOT EXISTS (SELECT 1 FROM public.revinfo_resumen s WHERE s.rev = r.rev)\n                "
                            + noRowsLeft));
        }

        String tables = Arrays.stream(ArchivedTable.values()).map(t -> "'" + t.table() + "'")
                .collect(Collectors.joining(", "));
        countPendingSql = "SELECT COALESCE(sum(s.cantidad), 0) FROM public.revinfo_resumen s"
                + " JOIN public.revinfo r ON r.rev = s.rev WHERE s.tabla_aud IN (" + tables + ") AND " + CANDIDATE;
    }

    /** Rows the cutoff would archive, from {@code revinfo_resumen}. */
    public long countPending(LocalDateTime cutoff) {
        Long total = jdbc.queryForObject(countPendingSql, new MapSqlParameterSource("cutoff", cutoff), Long.class);
        return total == null ? 0 : total;
    }

    /**
     * Tries the archive advisory lock (transaction scoped). Two-int form: another key space than
     * ClassroomAllocationLock (pg_advisory_xact_lock(bigint)).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryLock() {
        return Boolean.TRUE.equals(jdbc.getJdbcTemplate().queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtext('" + LOCK_NAME + "'), 0)", Boolean.class));
    }

    /** The batch uses a TEMP table: without this privilege the run would fail midway. */
    public boolean canCreateTempTables() {
        return Boolean.TRUE.equals(jdbc.getJdbcTemplate().queryForObject(
                "SELECT has_database_privilege(current_user, current_database(), 'TEMP')", Boolean.class));
    }

    /**
     * Moves one batch of {@code table}. Empty if another run holds the archive lock. Idempotent: if the
     * process dies, the next run picks the same rows again and ON CONFLICT DO NOTHING absorbs the copies.
     * MANDATORY: the advisory lock and the temp table live until the caller's commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ArchiveBatch> archiveBatch(ArchivedTable table, LocalDateTime cutoff, int batchSize) {
        if (!tryLock()) {
            return Optional.empty();
        }

        Statements sql = statements.get(table);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", cutoff)
                .addValue("batchSize", batchSize);

        // Not "CREATE TABLE AS": Postgres does not accept parameters there.
        jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS pg_temp.lote_archivo");
        jdbc.getJdbcTemplate().execute(
                "CREATE TEMP TABLE lote_archivo (rev integer, id bigint, revtype smallint) ON COMMIT DROP");
        jdbc.update(sql.select(), params);

        // revinfo first: every archived row must find its revision in archivo.revinfo.
        jdbc.getJdbcTemplate().update("INSERT INTO archivo.revinfo SELECT r.* FROM public.revinfo r"
                + " WHERE r.rev IN (SELECT rev FROM lote_archivo) ON CONFLICT (rev) DO NOTHING");
        // The first copy keeps the original count; later batches of the same revision hit the primary key.
        jdbc.getJdbcTemplate().update(sql.copyResumen());
        jdbc.getJdbcTemplate().update(sql.copyRows());
        // ON CONFLICT DO NOTHING may skip a row that already exists with other content: never delete before this holds.
        Long missing = jdbc.getJdbcTemplate().queryForObject(sql.verifyCopy(), Long.class);
        if (missing != null && missing > 0) {
            throw new IllegalStateException("La copia a archivo." + table.table() + " no coincide con el original en "
                    + missing + " filas; lote abortado antes de borrar");
        }
        int rows = jdbc.getJdbcTemplate().update(sql.delete());
        jdbc.getJdbcTemplate().update(sql.discount());
        jdbc.getJdbcTemplate().update(sql.deleteEmptyResumen());
        int deletedRevisions = jdbc.getJdbcTemplate().update(sql.deleteRevisions());
        return Optional.of(new ArchiveBatch(rows, deletedRevisions));
    }
}
