package ar.edu.utn.frc.siga.audit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V69 on its own database: Flyway migrates to V68, the test seeds revisions with the old release
 * descriptions (enum code in parentheses) in {@code public.revinfo} and {@code archivo.revinfo}, and then
 * migrates to V69. The shared Spring context already ran every migration, so the rewrite can't be observed there.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V69 descripción de auditoría con el motivo de liberación (integración)")
class ReleaseReasonDescriptionMigrationIntegrationTest {

    /** Old code and the text V69 must leave in its place. */
    private static final Map<String, String> REASONS = new LinkedHashMap<>();

    static {
        REASONS.put("VIRTUAL_CLASS", "Clase virtual");
        REASONS.put("CANCELLED", "Cancelada");
        REASONS.put("MIRROR_RETIRED", "Espejo retirado");
        REASONS.put("OTHER", "Otro");
        REASONS.put("CLASSROOM_DEACTIVATED", "Aula o edificio deshabilitado");
    }

    private static final int FIRST_REV = 1000;
    private static final int UNTOUCHED_BASE_REV = 2000;

    /** Rows V69 must not touch: no release prefix, no code, or a code that is not a release reason. */
    private static final String[] UNTOUCHED = {
            "Reversión de liberación de 2 ocurrencias (VIRTUAL_CLASS)",
            "Alta de evento recurrente (OTHER)",
            "Liberación de 1 ocurrencia (Otro)",
            "Liberación de 3 ocurrencias",
            "Liberación de 1 ocurrencia (DESCONOCIDO)",
            "Modificación de configuración"};

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateToV68SeedAndMigrateToV69() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("68").load().migrate();
        seed("public.revinfo");
        seed("archivo.revinfo");
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static void seed(String table) {
        int rev = FIRST_REV;
        for (String code : REASONS.keySet()) {
            insert(table, rev++, "Liberación de 1 ocurrencia (" + code + ")");
            insert(table, rev++, "Liberación de 12 ocurrencias (" + code + ")");
        }
        insert(table, rev, null);
        rev = UNTOUCHED_BASE_REV;
        for (String description : UNTOUCHED) {
            insert(table, rev++, description);
        }
    }

    private static void insert(String table, int rev, String description) {
        jdbc.update("INSERT INTO " + table + " (rev, fecha_revision, tipo_actor, descripcion) "
                + "VALUES (?, now(), 'HUMAN', ?)", rev, description);
    }

    private static String description(String table, int rev) {
        return jdbc.queryForObject("SELECT descripcion FROM " + table + " WHERE rev = ?", String.class, rev);
    }

    @Test
    @DisplayName("public.revinfo: cada código entre paréntesis pasa a su nombre visible y el resto del texto queda igual")
    void publicRevinfoRewritesEveryCode() {
        int rev = FIRST_REV;
        for (Map.Entry<String, String> reason : REASONS.entrySet()) {
            assertThat(description("public.revinfo", rev++))
                    .isEqualTo("Liberación de 1 ocurrencia (" + reason.getValue() + ")");
            assertThat(description("public.revinfo", rev++))
                    .isEqualTo("Liberación de 12 ocurrencias (" + reason.getValue() + ")");
        }
    }

    @Test
    @DisplayName("archivo.revinfo: cada código entre paréntesis pasa a su nombre visible y el resto del texto queda igual")
    void archiveRevinfoRewritesEveryCode() {
        int rev = FIRST_REV;
        for (Map.Entry<String, String> reason : REASONS.entrySet()) {
            assertThat(description("archivo.revinfo", rev++))
                    .isEqualTo("Liberación de 1 ocurrencia (" + reason.getValue() + ")");
            assertThat(description("archivo.revinfo", rev++))
                    .isEqualTo("Liberación de 12 ocurrencias (" + reason.getValue() + ")");
        }
    }

    @Test
    @DisplayName("las descripciones que no son una liberación con código quedan intactas en ambas tablas")
    void otherDescriptionsAreUntouched() {
        for (String table : new String[] {"public.revinfo", "archivo.revinfo"}) {
            for (int i = 0; i < UNTOUCHED.length; i++) {
                assertThat(description(table, UNTOUCHED_BASE_REV + i)).as(table).isEqualTo(UNTOUCHED[i]);
            }
            assertThat(description(table, FIRST_REV + 2 * REASONS.size())).as(table).isNull();
        }
    }

    @Test
    @DisplayName("ningún código interno queda en las descripciones de liberación")
    void noInternalCodeRemains() {
        for (String table : new String[] {"public.revinfo", "archivo.revinfo"}) {
            for (String code : REASONS.keySet()) {
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE descripcion LIKE ? AND rev < ?",
                        Long.class, "Liberación de %(" + code + ")", UNTOUCHED_BASE_REV)).as(table + " " + code).isZero();
            }
        }
    }
}
