package ar.edu.utn.frc.siga.academic;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V40 en bases propias: Flyway migra a V28, el test siembra comisiones y después migra a la última. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V40 UNIQUE de comision (integración)")
class CommissionUniqueMigrationIntegrationTest {

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate admin;
    private static int databaseCount;

    @BeforeAll
    static void startContainer() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        admin = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    @DisplayName("sin duplicados migra y después rechaza un par (curso, período) repetido")
    void migratesAndRejectsDuplicates() {
        DriverManagerDataSource dataSource = freshDatabaseMigratedTo28();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedPeriod(jdbc);
        insertCommission(jdbc, 1, "1K1");
        insertCommission(jdbc, 2, "1K2");

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM comision", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> insertCommission(jdbc, 3, "1K1")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("con duplicados la migración falla con un mensaje claro y no borra filas")
    void failsWithoutDeletingRowsWhenDuplicatesExist() {
        DriverManagerDataSource dataSource = freshDatabaseMigratedTo28();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedPeriod(jdbc);
        insertCommission(jdbc, 1, "1K1");
        insertCommission(jdbc, 2, "1K1");

        assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).load().migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("duplicados");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM comision", Integer.class)).isEqualTo(2);
    }

    private static DriverManagerDataSource freshDatabaseMigratedTo28() {
        String name = "migration_test_" + (++databaseCount);
        admin.execute("CREATE DATABASE " + name);
        String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                url, postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).target("28").load().migrate();
        return dataSource;
    }

    private static void seedPeriod(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO periodo_academico (id_periodo, anio, cuatrimestre) VALUES (1, 2026, 0)");
    }

    private static void insertCommission(JdbcTemplate jdbc, long id, String courseCode) {
        jdbc.update("INSERT INTO comision (id_comision, codigo_curso, id_periodo_academico) VALUES (?, ?, 1)",
                id, courseCode);
    }
}
