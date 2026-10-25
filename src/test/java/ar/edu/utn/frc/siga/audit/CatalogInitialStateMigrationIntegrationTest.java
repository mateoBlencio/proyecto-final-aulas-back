package ar.edu.utn.frc.siga.audit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V50 on its own databases: Flyway migrates to V49, the test seeds catalog rows, and then migrates to V50.
 * A second database migrated without catalog rows checks that no revision is opened. The shared Spring context
 * already ran every migration over an empty database, so the backfill can't be observed there.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Migración V50 de estado inicial de catálogos (integración)")
class CatalogInitialStateMigrationIntegrationTest {

    private static final String PREVIOUS_VERSION = "49";
    private static final String DESCRIPTION = "Estado inicial de catálogos";
    private static final String FIRST_USER = "primero@frc.utn.edu.ar";

    /** Audited table, id column of the base table, and rows seeded in it. */
    private record Catalog(String table, String idColumn, int rows) {
        String auditTable() {
            return table + "_aud";
        }
    }

    private static final List<Catalog> CATALOGS = List.of(
            new Catalog("edificio", "id_edificio", 2),
            new Catalog("aula", "id_aula", 2),
            new Catalog("tipo_aula", "id_tipo_aula", 1),
            new Catalog("aula_recurso", "id_aula_recurso", 1),
            new Catalog("aula_permiso", "id_aula_permiso", 1),
            new Catalog("tipo_recurso", "id_tipo_recurso", 1),
            new Catalog("feriado", "fecha", 1),
            new Catalog("comision", "id_comision", 1),
            new Catalog("materia", "id_materia", 1),
            new Catalog("especialidad", "id_especialidad", 2),
            new Catalog("plan_estudio", "id_plan", 1),
            new Catalog("periodo_academico", "id_periodo", 1));

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate withData;
    private static JdbcTemplate withoutData;

    @BeforeAll
    static void migrateBothDatabases() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()))
                .execute("CREATE DATABASE sin_catalogos");

        DriverManagerDataSource dataSource = dataSource(postgres.getDatabaseName());
        withData = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target(PREVIOUS_VERSION).load().migrate();
        seedCatalogs();
        Flyway.configure().dataSource(dataSource).load().migrate();

        DriverManagerDataSource emptyDataSource = dataSource("sin_catalogos");
        withoutData = new JdbcTemplate(emptyDataSource);
        Flyway.configure().dataSource(emptyDataSource).target(PREVIOUS_VERSION).load().migrate();
        withoutData.update("INSERT INTO usuario (id_usuario, correo, password_hash, nombre, apellido) "
                + "VALUES (1, ?, 'x', 'Primero', 'Usuario')", FIRST_USER);
        Flyway.configure().dataSource(emptyDataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static DriverManagerDataSource dataSource(String database) {
        String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName() + "?", "/" + database + "?");
        return new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
    }

    private static void seedCatalogs() {
        withData.update("INSERT INTO usuario (id_usuario, correo, password_hash, nombre, apellido) "
                + "VALUES (2, 'segundo@frc.utn.edu.ar', 'x', 'Segundo', 'Usuario')");
        withData.update("INSERT INTO usuario (id_usuario, correo, password_hash, nombre, apellido) "
                + "VALUES (1, ?, 'x', 'Primero', 'Usuario')", FIRST_USER);

        withData.update("INSERT INTO edificio (id_edificio, nombre, codigo_edificio) VALUES (1, 'Central', 1)");
        withData.update("INSERT INTO edificio (id_edificio, nombre, codigo_edificio, eliminado_en) "
                + "VALUES (2, 'Anexo', 2, now())");
        withData.update("INSERT INTO tipo_aula (id_tipo_aula, descripcion) VALUES (1, 'Laboratorio')");
        withData.update("INSERT INTO aula (id_aula, id_edificio, id_tipo_aula, capacidad, numero) "
                + "VALUES (10, 1, 1, 30, 101)");
        withData.update("INSERT INTO aula (id_aula, id_edificio, id_tipo_aula, capacidad, numero, eliminado_en) "
                + "VALUES (11, 1, 1, 20, 102, now())");
        withData.update("INSERT INTO tipo_recurso (id_tipo_recurso, nombre, tipo_valor, creado_en, actualizado_en) "
                + "VALUES (1, 'Proyector', 'COUNT', now(), now())");
        withData.update("INSERT INTO aula_recurso (id_aula_recurso, id_aula, id_tipo_recurso, cantidad, creado_en, "
                + "actualizado_en) VALUES (1, 10, 1, 2, now(), now())");
        withData.update("INSERT INTO aula_permiso (id_aula_permiso, id_aula, tipo_objetivo, id_objetivo, creado_en, "
                + "actualizado_en) VALUES (1, 10, 'SUBJECT', 5, now(), now())");
        withData.update("INSERT INTO feriado (fecha, descripcion, sincronizado_en) "
                + "VALUES (DATE '2026-07-09', 'Día de la Independencia', now())");
        withData.update("INSERT INTO periodo_academico (id_periodo, anio, cuatrimestre) VALUES (1, 2026, 1)");
        withData.update("INSERT INTO comision (id_comision, codigo_curso, id_periodo_academico) "
                + "VALUES (1, '5D1', 1)");
        withData.update("INSERT INTO especialidad (id_especialidad, codigo_especialidad, nombre) "
                + "VALUES (1, 1, 'Sistemas')");
        withData.update("INSERT INTO especialidad (id_especialidad, codigo_especialidad, nombre, eliminado_en) "
                + "VALUES (2, 2, 'Civil', now())");
        withData.update("INSERT INTO plan_estudio (id_plan, codigo_plan, id_especialidad) VALUES (1, 2023, 1)");
        withData.update("INSERT INTO materia (id_materia, codigo_materia, nombre, id_plan) "
                + "VALUES (5, 1, 'Física I', 1)");
    }

    private static Stream<Catalog> catalogs() {
        return CATALOGS.stream();
    }

    private static int seededRows() {
        return CATALOGS.stream().mapToInt(Catalog::rows).sum();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogs")
    @DisplayName("deja exactamente un ADD (revtype 0) por fila existente, sin repetir ids")
    void eachExistingRowGetsOneAddRecord(Catalog catalog) {
        Map<String, Object> counts = withData.queryForMap(
                "SELECT count(*) AS total, count(DISTINCT " + catalog.idColumn() + ") AS ids, "
                        + "count(*) FILTER (WHERE revtype = 0) AS adds FROM " + catalog.auditTable());

        assertThat(counts.get("total")).isEqualTo((long) catalog.rows());
        assertThat(counts.get("ids")).isEqualTo((long) catalog.rows());
        assertThat(counts.get("adds")).isEqualTo((long) catalog.rows());
    }

    @Test
    @DisplayName("copia el estado de las filas dadas de baja, con eliminado_en no nulo")
    void deletedRowsKeepTheirDeletionTimestamp() {
        assertThat(withData.queryForObject(
                "SELECT eliminado_en IS NOT NULL FROM aula_aud WHERE id_aula = 11", Boolean.class)).isTrue();
        assertThat(withData.queryForObject(
                "SELECT eliminado_en IS NULL FROM aula_aud WHERE id_aula = 10", Boolean.class)).isTrue();
        assertThat(withData.queryForObject(
                "SELECT descripcion FROM feriado_aud WHERE fecha = DATE '2026-07-09'", String.class))
                .isEqualTo("Día de la Independencia");
    }

    @Test
    @DisplayName("abre una sola revisión de sistema sin usuario")
    void opensASingleManualCorrectionRevision() {
        List<Map<String, Object>> revisions = withData.queryForList(
                "SELECT rev, usuario, tipo_actor FROM revinfo WHERE descripcion = ?", DESCRIPTION);

        assertThat(revisions).hasSize(1);
        assertThat(revisions.get(0)).containsEntry("usuario", null).containsEntry("tipo_actor", "SYSTEM");
        Integer rev = (Integer) revisions.get(0).get("rev");
        for (Catalog catalog : CATALOGS) {
            assertThat(withData.queryForObject(
                    "SELECT count(*) FROM " + catalog.auditTable() + " WHERE rev <> ?", Long.class, rev))
                    .as("filas de %s en otra revisión", catalog.auditTable()).isZero();
        }
    }

    @Test
    @DisplayName("revinfo_resumen suma una entrada por fila y por tabla _aud en esa revisión")
    void summaryMatchesTheRegisteredRows() {
        Integer rev = withData.queryForObject("SELECT rev FROM revinfo WHERE descripcion = ?", Integer.class,
                DESCRIPTION);

        assertThat(withData.queryForObject(
                "SELECT sum(cantidad) FROM revinfo_resumen WHERE rev = ?", Long.class, rev))
                .isEqualTo((long) seededRows());
        for (Catalog catalog : CATALOGS) {
            assertThat(withData.queryForObject(
                    "SELECT cantidad FROM revinfo_resumen WHERE rev = ? AND tabla_aud = ? AND revtype = 0",
                    Long.class, rev, catalog.auditTable()))
                    .as("resumen de %s", catalog.auditTable()).isEqualTo((long) catalog.rows());
        }
    }

    @Test
    @DisplayName("sobre una base sin filas de catálogo no crea ninguna revisión ni fila _aud")
    void emptyDatabaseCreatesNoRevision() {
        assertThat(withoutData.queryForObject("SELECT count(*) FROM revinfo", Long.class)).isZero();
        assertThat(withoutData.queryForObject("SELECT count(*) FROM revinfo_resumen", Long.class)).isZero();
        for (Catalog catalog : CATALOGS) {
            assertThat(withoutData.queryForObject("SELECT count(*) FROM " + catalog.auditTable(), Long.class))
                    .as("filas de %s", catalog.auditTable()).isZero();
        }
    }
}
