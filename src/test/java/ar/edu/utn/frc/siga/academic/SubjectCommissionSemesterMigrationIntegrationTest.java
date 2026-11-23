package ar.edu.utn.frc.siga.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V79 on its own database: Flyway migrates to V78, the test seeds a link and then migrates to the latest. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V79 cuatrimestre en materia-comisión (integración)")
class SubjectCommissionSemesterMigrationIntegrationTest {

    private static PostgreSQLContainer container;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateToV78SeedAndMigrate() {
        container = new PostgreSQLContainer("postgres:16-alpine");
        container.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("78").load().migrate();
        jdbc.update("INSERT INTO especialidad (id_especialidad, codigo_especialidad, nombre) VALUES (1, 5, 'Sistemas')");
        jdbc.update("INSERT INTO plan_estudio (id_plan, codigo_plan, id_especialidad) VALUES (1, 2008, 1)");
        for (int i = 1; i <= 6; i++) {
            jdbc.update("INSERT INTO materia (id_materia, codigo_materia, nombre, id_plan) VALUES (?, ?, 'M', 1)",
                    i, 100 + i);
        }
        jdbc.update("INSERT INTO periodo_academico (id_periodo, anio, cuatrimestre) VALUES (1, 2026, 0)");
        jdbc.update("INSERT INTO comision (id_comision, codigo_curso, id_periodo_academico) VALUES (1, '1K1', 1)");
        jdbc.update("INSERT INTO materia_comision (id_materia_comision, id_materia, id_comision, cantidad_inscriptos) "
                + "VALUES (1, 1, 1, 20)");
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    private static void insertLink(long id, Integer semester) {
        jdbc.update("INSERT INTO materia_comision (id_materia_comision, id_materia, id_comision, cantidad_inscriptos, cuatrimestre) "
                + "VALUES (?, ?, 1, 10, ?)", id, id, semester);
    }

    @Test
    @DisplayName("los vínculos existentes quedan con cuatrimestre null")
    void existingLinksKeepNullSemester() {
        assertThat(jdbc.queryForObject("SELECT cuatrimestre FROM materia_comision WHERE id_materia_comision = 1",
                Integer.class)).isNull();
    }

    @Test
    @DisplayName("acepta 0, 1, 2 y null")
    void acceptsValidValues() {
        insertLink(2, 0);
        insertLink(3, 1);
        insertLink(4, 2);
        insertLink(5, null);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM materia_comision WHERE id_materia_comision BETWEEN 2 AND 5", Integer.class))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("rechaza 3 y -1 por el CHECK")
    void rejectsOutOfRangeValues() {
        assertThatThrownBy(() -> insertLink(6, 3)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertLink(6, -1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("materia_comision_aud tiene la columna cuatrimestre")
    void auditTableHasSemesterColumn() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = 'materia_comision_aud' AND column_name = 'cuatrimestre'",
                Integer.class)).isEqualTo(1);
    }
}
