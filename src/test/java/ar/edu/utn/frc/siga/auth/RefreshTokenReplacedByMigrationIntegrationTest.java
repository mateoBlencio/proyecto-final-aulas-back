package ar.edu.utn.frc.siga.auth;

import static org.assertj.core.api.Assertions.assertThat;

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

/** V73 on its own database: Flyway migrates to V72, the test seeds a replacement chain and then migrates to the latest. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V73 reemplazado_por del refresh token con ON DELETE SET NULL (integración)")
class RefreshTokenReplacedByMigrationIntegrationTest {

    private static PostgreSQLContainer container;
    private static JdbcTemplate jdbc;
    private static boolean deleteBlockedBeforeV73;

    @BeforeAll
    static void migrateToV72SeedAndMigrate() {
        container = new PostgreSQLContainer("postgres:16-alpine");
        container.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("72").load().migrate();

        jdbc.update("INSERT INTO usuario (id_usuario, correo, password_hash, habilitado, nombre, apellido) "
                + "VALUES (1, 'v73@frc.utn.edu.ar', 'HASH', true, 'V73', 'Test')");
        // id 1: vencido y referenciado por el 2; id 2: sucesor activo; id 3: vencido sin referencias.
        insertToken(1, "expired", "now() - interval '31 days'", null);
        insertToken(2, "successor", "now() + interval '5 days'", 1L);
        insertToken(3, "orphanExpired", "now() - interval '31 days'", null);

        try {
            jdbc.update("DELETE FROM refresh_token WHERE id_refresh_token = 1");
        } catch (DataIntegrityViolationException e) {
            deleteBlockedBeforeV73 = true;
        }

        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    private static void insertToken(long id, String hash, String expiresAtSql, Long replacedBy) {
        jdbc.update("INSERT INTO refresh_token (id_refresh_token, id_usuario, token_hash, fecha_expiracion, reemplazado_por) "
                + "VALUES (?, 1, ?, " + expiresAtSql + ", ?)", id, hash, replacedBy);
    }

    @Test
    @DisplayName("antes de V73 borrar un token referenciado en reemplazado_por viola la FK")
    void deletingReferencedTokenWasBlockedBeforeTheMigration() {
        assertThat(deleteBlockedBeforeV73).isTrue();
    }

    @Test
    @DisplayName("la FK fk_refresh_token_reemplazado_por queda con ON DELETE SET NULL")
    void foreignKeyCascadesToNull() {
        String onDelete = jdbc.queryForObject(
                "SELECT confdeltype::text FROM pg_constraint WHERE conname = 'fk_refresh_token_reemplazado_por'",
                String.class);

        assertThat(onDelete).isEqualTo("n");
    }

    @Test
    @DisplayName("la migración conserva las filas y la referencia existente")
    void migrationKeepsRowsAndReference() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE id_refresh_token IN (1, 2, 3)", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT reemplazado_por FROM refresh_token WHERE id_refresh_token = 2", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("después de V73 borrar el token vencido deja reemplazado_por en NULL en el sucesor y no toca otras filas")
    void deletingReferencedTokenNullsTheReference() {
        insertToken(10, "expired2", "now() - interval '31 days'", null);
        insertToken(11, "successor2", "now() + interval '5 days'", 10L);

        jdbc.update("DELETE FROM refresh_token WHERE id_refresh_token = 10");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE id_refresh_token = 10", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE id_refresh_token = 11 AND reemplazado_por IS NULL",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE id_refresh_token = 3", Integer.class)).isEqualTo(1);
    }
}
