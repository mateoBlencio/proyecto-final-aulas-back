package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.audit.repository.AuditArchiveRepository;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Privilege block of V19 on its own database. The shared Spring database has no grants (the user owns
 * everything), so the block never runs there. Here Flyway migrates to V18, the test creates roles with grants
 * on {@code public.ocurrencia_aud}, and then V19 replicates them.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V19: privilegios del schema archivo (integración)")
class AuditArchivePrivilegesMigrationIntegrationTest {

    private static final int ARCHIVE_VERSION = 19;
    private static final List<String> ARCHIVE_TABLES = List.of("archivo.revinfo", "archivo.revinfo_resumen",
            "archivo.ocurrencia_aud", "archivo.asignacion_aula_aud", "public.archivado_auditoria",
            "public.archivado_auditoria_aud");

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateWithRolesInPlace() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        DriverManagerDataSource owner = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        jdbc = new JdbcTemplate(owner);

        Flyway.configure().dataSource(owner).target(String.valueOf(ARCHIVE_VERSION - 1)).load().migrate();
        jdbc.execute("CREATE ROLE app_rw LOGIN PASSWORD 'app_rw'");
        jdbc.execute("CREATE ROLE app_reader LOGIN PASSWORD 'app_reader'");
        jdbc.execute("CREATE ROLE stranger LOGIN PASSWORD 'stranger'");
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON public.ocurrencia_aud TO app_rw");
        jdbc.execute("GRANT SELECT ON public.revinfo TO app_rw");
        jdbc.execute("GRANT SELECT ON public.ocurrencia_aud TO app_reader");
        jdbc.execute("GRANT SELECT ON public.asignacion_aula_aud TO stranger");
        // stranger holds nothing on ocurrencia_aud, which is the table the block keys on.
        jdbc.execute("REVOKE TEMP ON DATABASE " + postgres.getDatabaseName() + " FROM PUBLIC");
        jdbc.execute("GRANT TEMP ON DATABASE " + postgres.getDatabaseName() + " TO app_rw");
        Flyway.configure().dataSource(owner).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static boolean tablePrivilege(String role, String table, String privilege) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT has_table_privilege(?, ?, ?)", Boolean.class,
                role, table, privilege));
    }

    private static boolean schemaUsage(String role) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT has_schema_privilege(?, 'archivo', 'USAGE')",
                Boolean.class, role));
    }

    @Test
    @DisplayName("un rol con SELECT, INSERT, UPDATE y DELETE sobre ocurrencia_aud recibe USAGE en archivo y los mismos privilegios en las seis tablas")
    void roleWithFullPrivilegesGetsThemOnTheArchive() {
        assertThat(schemaUsage("app_rw")).isTrue();
        for (String table : ARCHIVE_TABLES) {
            for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
                assertThat(tablePrivilege("app_rw", table, privilege)).as("%s %s", privilege, table).isTrue();
            }
        }
    }

    @Test
    @DisplayName("un rol de solo lectura recibe SELECT pero no INSERT ni DELETE: replica lo que tenía, no más")
    void readOnlyRoleKeepsBeingReadOnly() {
        assertThat(schemaUsage("app_reader")).isTrue();
        for (String table : ARCHIVE_TABLES) {
            assertThat(tablePrivilege("app_reader", table, "SELECT")).as("SELECT %s", table).isTrue();
            assertThat(tablePrivilege("app_reader", table, "INSERT")).as("INSERT %s", table).isFalse();
            assertThat(tablePrivilege("app_reader", table, "DELETE")).as("DELETE %s", table).isFalse();
        }
    }

    @Test
    @DisplayName("un rol sin privilegios sobre ocurrencia_aud no recibe nada en archivo")
    void roleWithoutPrivilegesOnTheKeyTableGetsNothing() {
        assertThat(schemaUsage("stranger")).isFalse();
        for (String table : ARCHIVE_TABLES) {
            assertThat(tablePrivilege("stranger", table, "SELECT")).as("SELECT %s", table).isFalse();
        }
    }

    @Test
    @DisplayName("una tabla creada después de la migración en archivo hereda los privilegios por defecto")
    void tableCreatedLaterInheritsTheGrants() {
        jdbc.execute("CREATE TABLE archivo.creada_despues (id integer)");

        assertThat(tablePrivilege("app_rw", "archivo.creada_despues", "DELETE")).isTrue();
        assertThat(tablePrivilege("app_rw", "archivo.creada_despues", "INSERT")).isTrue();
        assertThat(tablePrivilege("app_reader", "archivo.creada_despues", "SELECT")).isTrue();
        assertThat(tablePrivilege("app_reader", "archivo.creada_despues", "INSERT")).isFalse();
        assertThat(tablePrivilege("stranger", "archivo.creada_despues", "SELECT")).isFalse();
    }

    @Test
    @DisplayName("el rol app_rw puede mover una fila por SQL (copiar, borrar) entre public y archivo")
    void appRoleCanActuallyMoveRows() {
        JdbcTemplate app = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "app_rw", "app_rw"));
        jdbc.update("INSERT INTO public.revinfo (rev, fecha_revision, tipo_actor) VALUES (500, now(), 'SYSTEM')");
        jdbc.update("INSERT INTO public.ocurrencia_aud (id_ocurrencia, rev, revtype) VALUES (1, 500, 0)");

        app.update("INSERT INTO archivo.revinfo SELECT r.* FROM public.revinfo r WHERE r.rev = 500");
        app.update("INSERT INTO archivo.ocurrencia_aud SELECT a.* FROM public.ocurrencia_aud a WHERE a.rev = 500");
        int deleted = app.update("DELETE FROM public.ocurrencia_aud WHERE rev = 500");

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM archivo.ocurrencia_aud WHERE rev = 500", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("canCreateTempTables es true para el rol con TEMP y false para el que no lo tiene")
    void tempPrivilegeCheck() {
        assertThat(repositoryAs("app_rw").canCreateTempTables()).isTrue();
        assertThat(repositoryAs("app_reader").canCreateTempTables()).isFalse();
    }

    private static AuditArchiveRepository repositoryAs(String role) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), role, role);
        return new AuditArchiveRepository(new NamedParameterJdbcTemplate(dataSource), mock(AuditedEntityRegistry.class));
    }
}
