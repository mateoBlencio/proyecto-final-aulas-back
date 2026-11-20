package ar.edu.utn.frc.siga.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V66 on its own databases: Flyway migrates to V65, the test seeds links and then migrates to the latest. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V66 materia-comisión sin baja por inscriptos (integración)")
class SubjectCommissionNoEnrolledMigrationIntegrationTest {

    private static final String REACTIVATION = "Reactivación de materia-comisión dada de baja por SysAcad sin inscriptos";

    private static PostgreSQLContainer withNoEnrolled;
    private static PostgreSQLContainer withoutNoEnrolled;
    private static JdbcTemplate jdbc;
    private static JdbcTemplate jdbcWithout;

    @BeforeAll
    static void migrateToV65SeedAndMigrate() {
        withNoEnrolled = new PostgreSQLContainer("postgres:16-alpine");
        withNoEnrolled.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                withNoEnrolled.getJdbcUrl(), withNoEnrolled.getUsername(), withNoEnrolled.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("65").load().migrate();
        seedCatalog(jdbc);
        // id 1: baja del sync por cero inscriptos; id 2: baja a mano; id 3: activo.
        jdbc.update("INSERT INTO materia_comision (id_materia_comision, id_materia, id_comision, cantidad_inscriptos, eliminado_en, motivo_baja) "
                + "VALUES (1, 1, 1, 0, now(), 'NO_ENROLLED')");
        jdbc.update("INSERT INTO materia_comision (id_materia_comision, id_materia, id_comision, cantidad_inscriptos, eliminado_en) "
                + "VALUES (2, 2, 1, 15, now())");
        jdbc.update("INSERT INTO materia_comision (id_materia_comision, id_materia, id_comision, cantidad_inscriptos) "
                + "VALUES (3, 3, 1, 20)");
        Flyway.configure().dataSource(dataSource).load().migrate();

        withoutNoEnrolled = new PostgreSQLContainer("postgres:16-alpine");
        withoutNoEnrolled.start();
        DriverManagerDataSource dataSourceWithout = new DriverManagerDataSource(
                withoutNoEnrolled.getJdbcUrl(), withoutNoEnrolled.getUsername(), withoutNoEnrolled.getPassword());
        jdbcWithout = new JdbcTemplate(dataSourceWithout);
        Flyway.configure().dataSource(dataSourceWithout).target("65").load().migrate();
        seedCatalog(jdbcWithout);
        jdbcWithout.update("INSERT INTO materia_comision (id_materia_comision, id_materia, id_comision, cantidad_inscriptos, eliminado_en) "
                + "VALUES (2, 2, 1, 15, now())");
        Flyway.configure().dataSource(dataSourceWithout).load().migrate();
    }

    @AfterAll
    static void stopContainers() {
        if (withNoEnrolled != null) {
            withNoEnrolled.stop();
        }
        if (withoutNoEnrolled != null) {
            withoutNoEnrolled.stop();
        }
    }

    private static void seedCatalog(JdbcTemplate template) {
        template.update("INSERT INTO especialidad (id_especialidad, codigo_especialidad, nombre) VALUES (1, 5, 'Sistemas')");
        template.update("INSERT INTO plan_estudio (id_plan, codigo_plan, id_especialidad) VALUES (1, 2008, 1)");
        template.update("INSERT INTO materia (id_materia, codigo_materia, nombre, id_plan) VALUES (1, 101, 'Análisis I', 1)");
        template.update("INSERT INTO materia (id_materia, codigo_materia, nombre, id_plan) VALUES (2, 102, 'Física I', 1)");
        template.update("INSERT INTO materia (id_materia, codigo_materia, nombre, id_plan) VALUES (3, 103, 'Álgebra', 1)");
        template.update("INSERT INTO periodo_academico (id_periodo, anio, cuatrimestre) VALUES (1, 2026, 0)");
        template.update("INSERT INTO comision (id_comision, codigo_curso, id_periodo_academico) VALUES (1, '1K1', 1)");
    }

    @Test
    @DisplayName("el vínculo dado de baja por cero inscriptos queda activo")
    void noEnrolledLinkIsReactivated() {
        assertThat(jdbc.queryForObject("SELECT eliminado_en FROM materia_comision WHERE id_materia_comision = 1",
                java.sql.Timestamp.class)).isNull();
    }

    @Test
    @DisplayName("el vínculo dado de baja a mano y el activo no cambian")
    void manualAndActiveLinksStayAsTheyWere() {
        assertThat(jdbc.queryForObject("SELECT eliminado_en IS NOT NULL FROM materia_comision WHERE id_materia_comision = 2",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT eliminado_en IS NULL FROM materia_comision WHERE id_materia_comision = 3",
                Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("la reactivación queda en materia_comision_aud como modificación de una revisión SYSTEM con la descripción nueva")
    void reactivationIsAuditedAsSystemRevision() {
        List<Map<String, Object>> audited = jdbc.queryForList(
                "SELECT a.id_materia_comision, a.revtype, a.eliminado_en, r.tipo_actor, r.descripcion "
                        + "FROM materia_comision_aud a JOIN revinfo r ON r.rev = a.rev");

        assertThat(audited).hasSize(1);
        Map<String, Object> row = audited.get(0);
        assertThat(((Number) row.get("id_materia_comision")).longValue()).isEqualTo(1L);
        assertThat(((Number) row.get("revtype")).intValue()).isEqualTo(1);
        assertThat(row.get("eliminado_en")).isNull();
        assertThat(row.get("tipo_actor")).isEqualTo("SYSTEM");
        assertThat(row.get("descripcion")).isEqualTo(REACTIVATION);
    }

    @Test
    @DisplayName("la columna motivo_baja ya no existe en materia_comision y sigue en materia_comision_aud")
    void motivoBajaColumnIsDroppedFromBaseTableOnly() {
        assertThat(columnExists("materia_comision")).isFalse();
        assertThat(columnExists("materia_comision_aud")).isTrue();
    }

    @Test
    @DisplayName("sin vínculos NO_ENROLLED no se crea ninguna revisión")
    void withoutNoEnrolledRowsNoRevisionIsCreated() {
        assertThat(jdbcWithout.queryForObject("SELECT count(*) FROM revinfo WHERE descripcion LIKE ?", Integer.class,
                "%" + REACTIVATION + "%")).isZero();
        assertThat(jdbcWithout.queryForObject("SELECT count(*) FROM materia_comision_aud", Integer.class)).isZero();
        assertThat(jdbcWithout.queryForObject("SELECT eliminado_en IS NOT NULL FROM materia_comision WHERE id_materia_comision = 2",
                Boolean.class)).isTrue();
    }

    private static boolean columnExists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = 'motivo_baja')",
                Boolean.class, table));
    }
}
