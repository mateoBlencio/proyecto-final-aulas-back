package ar.edu.utn.frc.siga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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

/** V76 and V77 on their own database: Flyway migrates to V75, the test seeds role assignments and then migrates to the latest. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V76 y V77 renombre de SUBSECRETARIA a ADMINISTRADOR (integración)")
class RoleRenameMigrationIntegrationTest {

    private static final long ADMIN_USER = 1L;
    private static final long AUXILIARY_USER = 2L;

    private static PostgreSQLContainer container;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateToV75SeedAndMigrate() {
        container = new PostgreSQLContainer("postgres:16-alpine");
        container.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("75").load().migrate();

        jdbc.update("INSERT INTO edificio (id_edificio, nombre) VALUES (1, 'Central')");
        insertUser(ADMIN_USER);
        insertUser(AUXILIARY_USER);

        insertAssignment(1, ADMIN_USER, "SUBSECRETARIA", "GLOBAL", null);
        insertAssignment(2, ADMIN_USER, "AUXILIAR_AULICO", "BUILDING", 1L);
        insertAssignment(3, ADMIN_USER, "CONSULTA", "GLOBAL", null);
        insertAssignment(4, AUXILIARY_USER, "AUXILIAR_AULICO", "BUILDING", 1L);

        jdbc.update("INSERT INTO revinfo (rev, fecha_revision, usuario, tipo_actor) VALUES (1, now(), NULL, 'SYSTEM')");
        insertAudit(1, ADMIN_USER, "SUBSECRETARIA");
        insertAudit(2, ADMIN_USER, "AUXILIAR_AULICO");
        insertAudit(4, AUXILIARY_USER, "AUXILIAR_AULICO");

        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    private static void insertUser(long id) {
        jdbc.update("INSERT INTO usuario (id_usuario, correo, password_hash, habilitado, nombre, apellido) "
                + "VALUES (?, ?, 'HASH', true, 'Rename', 'Test')", id, "rename" + id + "@frc.utn.edu.ar");
    }

    private static void insertAssignment(long id, long userId, String role, String scopeType, Long scopeId) {
        jdbc.update("INSERT INTO usuario_rol (id_usuario_rol, id_usuario, rol, tipo_alcance, id_alcance) VALUES (?, ?, ?, ?, ?)",
                id, userId, role, scopeType, scopeId);
    }

    private static void insertAudit(long assignmentId, long userId, String role) {
        jdbc.update("INSERT INTO usuario_rol_aud (id_usuario_rol, id_usuario, rev, revtype, rol) VALUES (?, ?, 1, 0, ?)",
                assignmentId, userId, role);
    }

    private static List<String> rolesOf(String table, long userId) {
        return jdbc.queryForList("SELECT rol FROM " + table + " WHERE id_usuario = ? ORDER BY rol", String.class, userId);
    }

    @Test
    @DisplayName("el usuario con SUBSECRETARIA queda solo con ADMINISTRADOR en usuario_rol")
    void subsecretariaUserKeepsOnlyAdministrador() {
        assertThat(rolesOf("usuario_rol", ADMIN_USER)).containsExactly("ADMINISTRADOR");
        assertThat(jdbc.queryForObject(
                "SELECT tipo_alcance FROM usuario_rol WHERE id_usuario = ? AND rol = 'ADMINISTRADOR'",
                String.class, ADMIN_USER)).isEqualTo("GLOBAL");
    }

    @Test
    @DisplayName("usuario_rol_aud no conserva SUBSECRETARIA y la fila pasa a ADMINISTRADOR")
    void auditRowsAreRenamed() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM usuario_rol_aud WHERE rol = 'SUBSECRETARIA'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT rol FROM usuario_rol_aud WHERE id_usuario_rol = 1", String.class)).isEqualTo("ADMINISTRADOR");
    }

    @Test
    @DisplayName("el usuario que solo tenía AUXILIAR_AULICO no cambia")
    void auxiliaryOnlyUserIsUntouched() {
        assertThat(rolesOf("usuario_rol", AUXILIARY_USER)).containsExactly("AUXILIAR_AULICO");
        assertThat(jdbc.queryForObject(
                "SELECT id_alcance FROM usuario_rol WHERE id_usuario = ?", Long.class, AUXILIARY_USER)).isEqualTo(1L);
        assertThat(rolesOf("usuario_rol_aud", AUXILIARY_USER)).containsExactly("AUXILIAR_AULICO");
    }

    @Test
    @DisplayName("la auditoría de AUXILIAR_AULICO del usuario administrador no se toca")
    void auditOfDeletedAssignmentsRemains() {
        assertThat(rolesOf("usuario_rol_aud", ADMIN_USER)).containsExactly("ADMINISTRADOR", "AUXILIAR_AULICO");
    }

    @Test
    @DisplayName("ADMINISTRADOR con alcance BUILDING viola chk_usuario_rol_alcance_por_rol")
    void administradorWithBuildingScopeViolatesCheck() {
        assertThatThrownBy(() -> insertAssignment(100, AUXILIARY_USER, "ADMINISTRADOR", "BUILDING", 1L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_usuario_rol_alcance_por_rol");
    }

    @Test
    @DisplayName("ADMINISTRADOR con alcance GLOBAL sigue permitido por el check")
    void administradorWithGlobalScopeIsAllowed() {
        insertUser(3);
        insertAssignment(101, 3, "ADMINISTRADOR", "GLOBAL", null);

        assertThat(rolesOf("usuario_rol", 3)).containsExactly("ADMINISTRADOR");
    }
}
