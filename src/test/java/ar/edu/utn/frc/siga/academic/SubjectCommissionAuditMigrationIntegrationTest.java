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

/** V53 on its own database: Flyway migrates to V52, the test seeds links and then migrates to the latest. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V53 auditoría de materia_comision (integración)")
class SubjectCommissionAuditMigrationIntegrationTest {

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateToV52SeedAndMigrate() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("52").load().migrate();
        jdbc.update("INSERT INTO especialidad (id_especialidad, codigo_especialidad, nombre) VALUES (1, 5, 'Sistemas')");
        jdbc.update("INSERT INTO plan_estudio (id_plan, codigo_plan, id_especialidad) VALUES (1, 2008, 1)");
        jdbc.update("INSERT INTO materia (id_materia, codigo_materia, nombre, id_plan) VALUES (1, 101, 'Análisis I', 1)");
        jdbc.update("INSERT INTO materia (id_materia, codigo_materia, nombre, id_plan) VALUES (2, 102, 'Física I', 1)");
        jdbc.update("INSERT INTO periodo_academico (id_periodo, anio, cuatrimestre) VALUES (1, 2026, 0)");
        jdbc.update("INSERT INTO comision (id_comision, codigo_curso, id_periodo_academico) VALUES (1, '1K1', 1)");
        jdbc.update("INSERT INTO materia_comision (id_materia, id_comision, cantidad_inscriptos) VALUES (1, 1, 30)");
        jdbc.update("INSERT INTO materia_comision (id_materia, id_comision, cantidad_inscriptos) VALUES (2, 1, 0)");
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    @DisplayName("cada vínculo existente recibe un id propio y conserva su par materia-comisión único")
    void existingLinksGetOwnIds() {
        List<Map<String, Object>> links = jdbc.queryForList(
                "SELECT id_materia_comision, id_materia, id_comision FROM materia_comision ORDER BY id_materia");

        assertThat(links).hasSize(2);
        assertThat(links).extracting(link -> link.get("id_materia_comision")).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("el estado inicial de cada vínculo queda como alta en la tabla _aud, sin la cantidad de inscriptos")
    void initialStateIsCopiedToAuditTable() {
        List<Map<String, Object>> audited = jdbc.queryForList(
                "SELECT a.revtype, a.id_materia, a.id_comision, a.es_presencial, r.tipo_actor, r.descripcion "
                        + "FROM materia_comision_aud a JOIN revinfo r ON r.rev = a.rev ORDER BY a.id_materia");

        assertThat(audited).hasSize(2);
        assertThat(audited).allSatisfy(row -> {
            assertThat(((Number) row.get("revtype")).intValue()).isZero();
            assertThat(row.get("tipo_actor")).isEqualTo("SYSTEM");
            assertThat(row.get("descripcion")).isEqualTo("Estado inicial de materia-comisión");
        });
        assertThat(audited).extracting(row -> ((Number) row.get("id_materia")).longValue()).containsExactly(1L, 2L);
    }
}
