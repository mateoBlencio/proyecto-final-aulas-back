package ar.edu.utn.frc.siga.audit;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seed and cleanup shared by the audit archive integration tests. Revisions are written by JDBC with an
 * explicit {@code fecha_revision} (R1 to R8 of the plan) and coherent {@code revinfo_resumen} rows. Cutoff
 * with {@link #TODAY} is 03/03/2003. Years 2002 to 2004 are used by no other test.
 */
final class AuditArchiveFixture {

    static final LocalDate TODAY = LocalDate.of(2004, 10, 1);
    static final LocalDate CUTOFF = LocalDate.of(2003, 3, 3);
    static final int RETAINED_FROM_CYCLE = 2003;

    private static final short ADD = 0;
    private static final List<String> ARCHIVE_TABLES =
            List.of("revinfo", "revinfo_resumen", "ocurrencia_aud", "asignacion_aula_aud");

    /** Revision numbers and operation ids of the seeded scenario. */
    record Scenario(int r1, int r2, int r3, int r4, int r5, int r6, int r7, int r8,
                    String sameOperation, String parentOperation, String childOperation) {

        List<Integer> archived() {
            return List.of(r1, r2, r3);
        }

        List<Integer> kept() {
            return List.of(r4, r5, r6, r7, r8);
        }
    }

    private final JdbcTemplate jdbc;
    private int baseline;

    AuditArchiveFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Takes the revision baseline and leaves periods and archive empty. */
    void begin() {
        baseline = jdbc.queryForObject("SELECT COALESCE(MAX(rev), 0) FROM revinfo", Integer.class);
        wipePeriods();
        truncateArchive();
    }

    /** Removes everything created since {@link #begin()}, including the revisions of the archive run. */
    void end() {
        jdbc.update("DELETE FROM archivado_auditoria_aud WHERE rev > ?", baseline);
        jdbc.update("DELETE FROM archivado_auditoria");
        jdbc.update("DELETE FROM ocurrencia_aud WHERE rev > ?", baseline);
        jdbc.update("DELETE FROM asignacion_aula_aud WHERE rev > ?", baseline);
        jdbc.update("DELETE FROM solicitud_aula_aud WHERE rev > ?", baseline);
        jdbc.update("DELETE FROM revinfo_resumen WHERE rev > ?", baseline);
        jdbc.update("DELETE FROM revinfo WHERE rev > ?", baseline);
        wipePeriods();
        truncateArchive();
    }

    int baseline() {
        return baseline;
    }

    private void wipePeriods() {
        jdbc.update("DELETE FROM periodo_academico WHERE anio BETWEEN 2002 AND 2004");
    }

    private void truncateArchive() {
        ARCHIVE_TABLES.forEach(table -> jdbc.execute("TRUNCATE archivo." + table));
    }

    void seedPeriods() {
        period(2002, LocalDate.of(2002, 3, 4));
        period(2003, LocalDate.of(2003, 3, 3));
        period(2004, LocalDate.of(2004, 3, 1));
    }

    private void period(int year, LocalDate start) {
        jdbc.update("INSERT INTO periodo_academico (anio, cuatrimestre, fecha_inicio, fecha_fin) VALUES (?, 0, ?, ?)",
                year, start, start.plusMonths(9));
    }

    Scenario seedScenario() {
        String same = UUID.randomUUID().toString();
        String parent = UUID.randomUUID().toString();
        String child = UUID.randomUUID().toString();

        int r1 = revision(LocalDateTime.of(2001, 5, 10, 10, 0), null, null);
        occurrences(r1, 9001, 3);
        allocations(r1, 9101, 2);

        int r2 = revision(LocalDateTime.of(2002, 6, 10, 10, 0), null, null);
        occurrences(r2, 9011, 2);
        jdbc.update("INSERT INTO solicitud_aula_aud (id_solicitud, rev, revtype, ambito, docente_email) "
                + "VALUES (9201, ?, ?, 'GRADO', 'docente@frc.utn.edu.ar')", r2, ADD);
        summary(r2, "solicitud_aula_aud", 1);

        int r3 = revision(LocalDateTime.of(2003, 1, 20, 10, 0), null, null);
        allocations(r3, 9111, 1);

        int r4 = revision(LocalDateTime.of(2003, 2, 28, 23, 59), same, null);
        occurrences(r4, 9021, 1);
        int r5 = revision(LocalDateTime.of(2003, 3, 3, 0, 10), same, null);
        occurrences(r5, 9022, 1);

        int r6 = revision(LocalDateTime.of(2003, 2, 27, 10, 0), parent, null);
        occurrences(r6, 9031, 1);
        int r7 = revision(LocalDateTime.of(2003, 3, 5, 10, 0), child, parent);
        occurrences(r7, 9032, 1);

        int r8 = revision(LocalDateTime.of(2003, 6, 1, 10, 0), null, null);
        occurrences(r8, 9041, 1);

        return new Scenario(r1, r2, r3, r4, r5, r6, r7, r8, same, parent, child);
    }

    int revision(LocalDateTime date, String operationId, String parentOperationId) {
        return jdbc.queryForObject(
                "INSERT INTO revinfo (fecha_revision, usuario, tipo_actor, descripcion, operacion_id, operacion_padre_id) "
                        + "VALUES (?, 'archive-test', 'HUMAN', ?, ?, ?) RETURNING rev",
                Integer.class, date, operationId == null ? null : "Operación de prueba", operationId, parentOperationId);
    }

    void occurrences(int rev, long firstId, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO ocurrencia_aud (id_ocurrencia, rev, revtype, id_evento_academico, fecha, estado) "
                    + "VALUES (?, ?, ?, ?, ?, 'NEEDS_ROOM')", firstId + i, rev, ADD, 500 + i, LocalDate.of(2001, 5, 10 + i));
        }
        summary(rev, "ocurrencia_aud", count);
    }

    void allocations(int rev, long firstId, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO asignacion_aula_aud (id_asignacion, rev, revtype, id_ocurrencia, id_aula, origen, "
                            + "fecha_creacion, observaciones) VALUES (?, ?, ?, ?, ?, 'MANUAL', ?, ?)",
                    firstId + i, rev, ADD, 9000 + i, 7 + i, LocalDateTime.of(2001, 5, 10, 9, i), "obs " + i);
        }
        summary(rev, "asignacion_aula_aud", count);
    }

    private void summary(int rev, String table, int count) {
        jdbc.update("INSERT INTO revinfo_resumen (rev, tabla_aud, revtype, cantidad) VALUES (?, ?, ?, ?)",
                rev, table, ADD, count);
    }

    /** Row counts of the four tables of both schemas, to compare before and after a run. */
    Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String schema : List.of("public", "archivo")) {
            for (String table : ARCHIVE_TABLES) {
                counts.put(schema + "." + table,
                        jdbc.queryForObject("SELECT count(*) FROM " + schema + "." + table, Long.class));
            }
        }
        return counts;
    }
}
