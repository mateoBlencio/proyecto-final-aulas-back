package ar.edu.utn.frc.siga.roomrequest;

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

/** V74 on its own database: Flyway migrates to V73, the test reads the columns, then migrates to the latest. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V74 motivo_reapertura en solicitud_aula_item (integración)")
class RoomRequestReopenReasonMigrationIntegrationTest {

    private static final List<String> TABLES = List.of("solicitud_aula_item", "solicitud_aula_item_aud");

    private static PostgreSQLContainer container;
    private static JdbcTemplate jdbc;
    private static boolean existedBeforeV74;

    @BeforeAll
    static void migrateToV73ThenToLatest() {
        container = new PostgreSQLContainer("postgres:16-alpine");
        container.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("73").load().migrate();
        existedBeforeV74 = TABLES.stream().anyMatch(table -> !columnInfo(table).isEmpty());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    private static List<Map<String, Object>> columnInfo(String table) {
        return jdbc.queryForList(
                "SELECT data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = 'motivo_reapertura'",
                table);
    }

    @Test
    @DisplayName("antes de V74 ninguna de las dos tablas tiene la columna")
    void columnDoesNotExistBeforeV74() {
        assertThat(existedBeforeV74).isFalse();
    }

    @Test
    @DisplayName("motivo_reapertura existe como varchar(255) nullable en solicitud_aula_item y solicitud_aula_item_aud")
    void columnExistsAndIsNullableInBothTables() {
        for (String table : TABLES) {
            List<Map<String, Object>> info = columnInfo(table);
            assertThat(info).as("columna motivo_reapertura en " + table).hasSize(1);
            assertThat(info.getFirst().get("data_type")).isEqualTo("character varying");
            assertThat(((Number) info.getFirst().get("character_maximum_length")).intValue()).isEqualTo(255);
            assertThat(info.getFirst().get("is_nullable")).isEqualTo("YES");
        }
    }

    @Test
    @DisplayName("motivo_decision sigue en ambas tablas: V74 agrega la columna sin tocar la anterior")
    void decisionColumnIsUntouched() {
        for (String table : TABLES) {
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = ? AND column_name = 'motivo_decision'",
                    Integer.class, table)).as("motivo_decision en " + table).isEqualTo(1);
        }
    }
}
