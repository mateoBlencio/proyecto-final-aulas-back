package ar.edu.utn.frc.siga.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V72 on its own database: Flyway migrates to V71, the test seeds holidays and then migrates to the latest. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V72 baja lógica de feriados (integración)")
class HolidayDeletedAtMigrationIntegrationTest {

    private static PostgreSQLContainer container;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateToV71SeedAndMigrate() {
        container = new PostgreSQLContainer("postgres:16-alpine");
        container.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("71").load().migrate();
        jdbc.update("INSERT INTO feriado (fecha, descripcion, sincronizado_en) VALUES ('2026-12-08', 'Inmaculada Concepción', now())");
        jdbc.update("INSERT INTO feriado (fecha, descripcion, sincronizado_en) VALUES ('2026-05-25', 'Revolución de Mayo', now())");
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    @DisplayName("eliminado_en existe y admite nulos en feriado y en feriado_aud")
    void columnExistsAndIsNullableInBothTables() {
        for (String table : List.of("feriado", "feriado_aud")) {
            List<String> nullable = jdbc.queryForList(
                    "SELECT is_nullable FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = ? AND column_name = 'eliminado_en'",
                    String.class, table);
            assertThat(nullable).as("columna eliminado_en en " + table).containsExactly("YES");
        }
    }

    @Test
    @DisplayName("los feriados existentes quedan vigentes, con eliminado_en nulo")
    void existingHolidaysStayActive() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feriado", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feriado WHERE eliminado_en IS NULL", Integer.class))
                .isEqualTo(2);
    }
}
