package ar.edu.utn.frc.siga.audit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Backfill of the {@code revinfo_resumen} migration on its own database: Flyway migrates to the previous
 * version, the test seeds historical {@code _aud} rows, and then migrates to the summary version.
 * The shared Spring context already ran every migration, so the backfill can't be observed there.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Migración de revinfo_resumen con datos previos (integración)")
class RevisionSummaryMigrationIntegrationTest {

    private static final short ADD = 0;
    private static final short MOD = 1;
    private static final short DEL = 2;

    private static final int REV_ASSIGNMENTS_MIXED = 1;
    private static final int REV_EVENT_RECURRING = 2;
    private static final int REV_EVENT_UNIQUE = 3;
    private static final int REV_USERS = 4;
    private static final int REV_SETTINGS = 5;
    private static final int REV_ROOM_REQUEST = 6;
    private static final int REV_WITHOUT_AUDIT_ROWS = 7;
    private static final int REV_ORPHAN = 999;

    private record Row(int rev, String table, int revtype, int count) {
    }

    private static PostgreSQLContainer postgres;
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateToPreviousSeedAndMigrateToSummary() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource)
                .target(String.valueOf(RevisionSummaryMigration.VERSION - 1)).load().migrate();
        seedHistoricalRevisions();
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static void seedHistoricalRevisions() {
        revision(REV_ASSIGNMENTS_MIXED);
        audit("asignacion_aula_aud", "id_asignacion", 1, REV_ASSIGNMENTS_MIXED, ADD);
        audit("asignacion_aula_aud", "id_asignacion", 2, REV_ASSIGNMENTS_MIXED, ADD);
        audit("asignacion_aula_aud", "id_asignacion", 3, REV_ASSIGNMENTS_MIXED, MOD);
        audit("asignacion_aula_aud", "id_asignacion", 4, REV_ASSIGNMENTS_MIXED, DEL);
        audit("ocurrencia_aud", "id_ocurrencia", 1, REV_ASSIGNMENTS_MIXED, MOD);

        revision(REV_EVENT_RECURRING);
        eventAudit(10, REV_EVENT_RECURRING, ADD);
        jdbc.update("INSERT INTO evento_recurrente_aud (id_evento_academico, rev) VALUES (10, ?)", REV_EVENT_RECURRING);
        for (int occurrence = 20; occurrence < 23; occurrence++) {
            audit("ocurrencia_aud", "id_ocurrencia", occurrence, REV_EVENT_RECURRING, ADD);
        }

        revision(REV_EVENT_UNIQUE);
        eventAudit(11, REV_EVENT_UNIQUE, ADD);
        jdbc.update("INSERT INTO evento_unico_aud (id_evento_academico, rev) VALUES (11, ?)", REV_EVENT_UNIQUE);

        revision(REV_USERS);
        audit("usuario_aud", "id_usuario", 1, REV_USERS, ADD);
        jdbc.update("INSERT INTO usuario_rol_aud (id_usuario_rol, id_usuario, rev, revtype, rol) VALUES (1, 1, ?, 0, 'SUBSECRETARIA')", REV_USERS);
        jdbc.update("INSERT INTO usuario_rol_aud (id_usuario_rol, id_usuario, rev, revtype, rol) VALUES (2, 1, ?, 0, 'AUXILIAR_AULICO')", REV_USERS);

        revision(REV_SETTINGS);
        setting("a", REV_SETTINGS, MOD);
        setting("b", REV_SETTINGS, MOD);
        setting("c", REV_SETTINGS, DEL);

        revision(REV_ROOM_REQUEST);
        audit("solicitud_aula_aud", "id_solicitud", 1, REV_ROOM_REQUEST, ADD);
        audit("solicitud_aula_item_aud", "id_item", 1, REV_ROOM_REQUEST, ADD);
        audit("solicitud_aula_item_aud", "id_item", 2, REV_ROOM_REQUEST, ADD);
        audit("solicitud_aula_preferencia_aud", "id_preferencia", 1, REV_ROOM_REQUEST, ADD);
        audit("solicitud_item_asignacion_aud", "id_item_asignacion", 1, REV_ROOM_REQUEST, ADD);

        revision(REV_WITHOUT_AUDIT_ROWS);

        // Every real _aud table has an FK to revinfo, so an orphan only exists in a database where that FK is gone.
        jdbc.execute("ALTER TABLE configuracion_aud DROP CONSTRAINT fk_configuracion_aud_rev");
        setting("orphan", REV_ORPHAN, ADD);
    }

    private static void revision(int rev) {
        jdbc.update("INSERT INTO revinfo (rev, fecha_revision, usuario, tipo_actor) VALUES (?, now(), NULL, 'SYSTEM')", rev);
    }

    private static void audit(String table, String idColumn, long id, int rev, short revtype) {
        jdbc.update("INSERT INTO " + table + " (" + idColumn + ", rev, revtype) VALUES (?, ?, ?)", id, rev, revtype);
    }

    private static void eventAudit(long id, int rev, short revtype) {
        jdbc.update("INSERT INTO evento_academico_aud (id_evento_academico, rev, revtype, tipo_evento) VALUES (?, ?, ?, 'RECURRING')",
                id, rev, revtype);
    }

    private static void setting(String key, int rev, short revtype) {
        jdbc.update("INSERT INTO configuracion_aud (clave, rev, revtype, valor) VALUES (?, ?, ?, 'x')", key, rev, revtype);
    }

    private static List<Row> summary() {
        return jdbc.query("SELECT rev, tabla_aud, revtype, cantidad FROM revinfo_resumen ORDER BY rev, tabla_aud, revtype",
                (rs, i) -> new Row(rs.getInt("rev"), rs.getString("tabla_aud"), rs.getInt("revtype"), rs.getInt("cantidad")));
    }

    private static List<Row> summaryOf(int rev) {
        return summary().stream().filter(row -> row.rev() == rev).toList();
    }

    @Test
    @DisplayName("el backfill produce exactamente una fila por (rev, tabla raíz, revtype) con su cantidad")
    void backfill_producesExactlyTheExpectedRows() {
        assertThat(summary()).containsExactlyInAnyOrder(
                new Row(REV_ASSIGNMENTS_MIXED, "asignacion_aula_aud", ADD, 2),
                new Row(REV_ASSIGNMENTS_MIXED, "asignacion_aula_aud", MOD, 1),
                new Row(REV_ASSIGNMENTS_MIXED, "asignacion_aula_aud", DEL, 1),
                new Row(REV_ASSIGNMENTS_MIXED, "ocurrencia_aud", MOD, 1),
                new Row(REV_EVENT_RECURRING, "evento_academico_aud", ADD, 1),
                new Row(REV_EVENT_RECURRING, "ocurrencia_aud", ADD, 3),
                new Row(REV_EVENT_UNIQUE, "evento_academico_aud", ADD, 1),
                new Row(REV_USERS, "usuario_aud", ADD, 1),
                new Row(REV_USERS, "usuario_rol_aud", ADD, 2),
                new Row(REV_SETTINGS, "configuracion_aud", MOD, 2),
                new Row(REV_SETTINGS, "configuracion_aud", DEL, 1),
                new Row(REV_ROOM_REQUEST, "solicitud_aula_aud", ADD, 1),
                new Row(REV_ROOM_REQUEST, "solicitud_aula_item_aud", ADD, 2),
                new Row(REV_ROOM_REQUEST, "solicitud_aula_preferencia_aud", ADD, 1),
                new Row(REV_ROOM_REQUEST, "solicitud_item_asignacion_aud", ADD, 1));
    }

    @Test
    @DisplayName("una revisión con varios revtype queda con una fila por tipo")
    void revisionWithSeveralRevtypes_hasOneRowPerType() {
        assertThat(summaryOf(REV_ASSIGNMENTS_MIXED))
                .filteredOn(row -> row.table().equals("asignacion_aula_aud"))
                .extracting(Row::revtype, Row::count)
                .containsExactlyInAnyOrder(
                        tuple(0, 2),
                        tuple(1, 1),
                        tuple(2, 1));
    }

    @Test
    @DisplayName("las filas de evento_recurrente_aud y evento_unico_aud no generan filas de resumen")
    void joinedSubclassTables_areNotCounted() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_recurrente_aud", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_unico_aud", Integer.class)).isEqualTo(1);
        assertThat(summary()).extracting(Row::table)
                .doesNotContain("evento_recurrente_aud", "evento_unico_aud");
        assertThat(summaryOf(REV_EVENT_RECURRING))
                .filteredOn(row -> row.table().equals("evento_academico_aud"))
                .containsExactly(new Row(REV_EVENT_RECURRING, "evento_academico_aud", ADD, 1));
    }

    @Test
    @DisplayName("una fila _aud huérfana (rev inexistente en revinfo) se omite y no rompe la migración")
    void orphanAuditRow_isSkipped() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM configuracion_aud WHERE rev = ?", Integer.class, REV_ORPHAN))
                .isEqualTo(1);
        assertThat(summaryOf(REV_ORPHAN)).isEmpty();
    }

    @Test
    @DisplayName("una revisión sin filas _aud no genera filas de resumen")
    void revisionWithoutAuditRows_hasNoSummary() {
        assertThat(summaryOf(REV_WITHOUT_AUDIT_ROWS)).isEmpty();
    }

    @Test
    @DisplayName("la tabla rechaza una fila de resumen cuya revisión no existe y un par (rev, tabla, revtype) repetido")
    void tableConstraints_rejectOrphanAndDuplicate() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO revinfo_resumen (rev, tabla_aud, revtype, cantidad) VALUES (?, 'configuracion_aud', 0, 1)",
                REV_ORPHAN))
                .hasMessageContaining("revinfo_resumen_rev_fkey");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO revinfo_resumen (rev, tabla_aud, revtype, cantidad) VALUES (?, 'configuracion_aud', 1, 1)",
                REV_SETTINGS))
                .hasMessageContaining("revinfo_resumen_pkey");
    }
}
