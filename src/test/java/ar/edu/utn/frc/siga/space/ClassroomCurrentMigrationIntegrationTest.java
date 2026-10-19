package ar.edu.utn.frc.siga.space;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** V41 en su propia base: Flyway migra a V40, el test siembra aulas y después migra a la última. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V41 vigente_sysacad en aula (integración)")
class ClassroomCurrentMigrationIntegrationTest {

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateToV40SeedAndMigrate() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("40").load().migrate();
        jdbc.update("INSERT INTO edificio (id_edificio, nombre) VALUES (1, 'Edificio')");
        jdbc.update("INSERT INTO tipo_aula (id_tipo_aula, descripcion) VALUES (1, 'Normal')");
        insertClassroom(1, 101, false, "hash");
        insertClassroom(2, 102, true, "hash");
        insertClassroom(3, 103, false, null);
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static void insertClassroom(long id, int number, boolean enabled, String hash) {
        jdbc.update("INSERT INTO aula (id_aula, id_edificio, id_tipo_aula, capacidad, numero, habilitada_sysacad, "
                + "hash_sysacad) VALUES (?, 1, 1, 40, ?, ?, ?)", id, number, enabled, hash);
    }

    private static Map<String, Object> classroom(long id) {
        return jdbc.queryForMap("SELECT habilitada_sysacad, vigente_sysacad FROM aula WHERE id_aula = ?", id);
    }

    @Test
    @DisplayName("las aulas sincronizadas quedan habilitadas y vigentes hasta la próxima corrida, que corrige el flag")
    void syncedClassroomsAreResetToEnabledAndCurrent() {
        assertThat(classroom(1)).containsEntry("habilitada_sysacad", true).containsEntry("vigente_sysacad", true);
        assertThat(classroom(2)).containsEntry("habilitada_sysacad", true).containsEntry("vigente_sysacad", true);
    }

    @Test
    @DisplayName("un aula cargada a mano (hash nulo) conserva su flag")
    void manualClassroomKeepsItsFlag() {
        assertThat(classroom(3)).containsEntry("habilitada_sysacad", false).containsEntry("vigente_sysacad", true);
    }
}
