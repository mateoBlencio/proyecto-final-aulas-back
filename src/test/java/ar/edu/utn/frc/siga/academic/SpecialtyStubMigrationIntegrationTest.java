package ar.edu.utn.frc.siga.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
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

/** V67 on its own database: Flyway migrates to V66, the test seeds specialties and their audit rows, then migrates to the latest. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V67 especialidades stub activas (integración)")
class SpecialtyStubMigrationIntegrationTest {

    private static final int REV_CREATED_BY_SYNC = 10;
    private static final int REV_MANUAL_DEACTIVATION = 11;
    private static final int REV_INITIAL_STATE = 12;
    private static final Timestamp DELETED_AT = Timestamp.valueOf("2026-03-04 10:00:00");

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateToV66SeedAndMigrate() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("66").load().migrate();
        revision(REV_CREATED_BY_SYNC, "SYSTEM", "Sincronización de especialidades desde SysAcad");
        revision(REV_MANUAL_DEACTIVATION, "HUMAN", "Baja de especialidad");
        revision(REV_INITIAL_STATE, "SYSTEM", "Estado inicial de catálogos");

        // 1: stub sin hash nacido de baja. 2: stub que SysAcad completó después (con hash), sigue de baja.
        // 3: especialidad real dada de baja a mano. 4: de baja desde antes de existir la auditoría (estado inicial).
        specialty(1, 91, null, null, DELETED_AT);
        specialty(2, 92, "Especialidad completada", "hash-2", DELETED_AT);
        specialty(3, 93, "Especialidad real", "hash-3", DELETED_AT);
        specialty(4, 94, null, null, DELETED_AT);
        audit(1, REV_CREATED_BY_SYNC, 0, DELETED_AT);
        audit(2, REV_CREATED_BY_SYNC, 0, DELETED_AT);
        audit(3, REV_CREATED_BY_SYNC, 0, null);
        audit(3, REV_MANUAL_DEACTIVATION, 1, DELETED_AT);
        audit(4, REV_INITIAL_STATE, 0, DELETED_AT);
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static void revision(int rev, String actor, String description) {
        jdbc.update("INSERT INTO revinfo (rev, fecha_revision, usuario, tipo_actor, descripcion) VALUES (?, now(), NULL, ?, ?)",
                rev, actor, description);
    }

    private static void specialty(long id, int code, String name, String hash, Timestamp deletedAt) {
        jdbc.update("INSERT INTO especialidad (id_especialidad, codigo_especialidad, nombre, hash_raw, eliminado_en) VALUES (?, ?, ?, ?, ?)",
                id, code, name, hash, deletedAt);
    }

    private static void audit(long id, int rev, int revtype, Timestamp deletedAt) {
        jdbc.update("INSERT INTO especialidad_aud (id_especialidad, rev, revtype, codigo_especialidad, eliminado_en) VALUES (?, ?, ?, ?, ?)",
                id, rev, revtype, 90 + id, deletedAt);
    }

    private static boolean isDeleted(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT eliminado_en IS NOT NULL FROM especialidad WHERE id_especialidad = ?", Boolean.class, id));
    }

    @Test
    @DisplayName("el stub sin hash y el stub completado después quedan activos")
    void bothStubsAreReactivated() {
        assertThat(isDeleted(1)).isFalse();
        assertThat(isDeleted(2)).isFalse();
    }

    @Test
    @DisplayName("la especialidad real dada de baja a mano sigue dada de baja")
    void manuallyDeactivatedSpecialtyStaysDeactivated() {
        assertThat(isDeleted(3)).isTrue();
    }

    @Test
    @DisplayName("la especialidad de baja desde el estado inicial de catálogos no se toca")
    void specialtyDeactivatedBeforeAuditExistedStaysDeactivated() {
        assertThat(isDeleted(4)).isTrue();
    }

    @Test
    @DisplayName("cada stub reactivado queda como modificación en la revisión SYSTEM nueva y los demás no aparecen en ella")
    void reactivatedStubsAreAuditedInOneSystemRevision() {
        List<Map<String, Object>> audited = jdbc.queryForList(
                "SELECT a.id_especialidad, a.revtype, a.eliminado_en, r.tipo_actor, r.descripcion "
                        + "FROM especialidad_aud a JOIN revinfo r ON r.rev = a.rev "
                        + "WHERE a.rev NOT IN (?, ?, ?) ORDER BY a.id_especialidad",
                REV_CREATED_BY_SYNC, REV_MANUAL_DEACTIVATION, REV_INITIAL_STATE);

        assertThat(audited).hasSize(2);
        assertThat(audited).extracting(row -> ((Number) row.get("id_especialidad")).longValue()).containsExactly(1L, 2L);
        assertThat(audited).allSatisfy(row -> {
            assertThat(((Number) row.get("revtype")).intValue()).isEqualTo(1);
            assertThat(row.get("eliminado_en")).isNull();
            assertThat(row.get("tipo_actor")).isEqualTo("SYSTEM");
            assertThat(row.get("descripcion"))
                    .isEqualTo("Reactivación de especialidades creadas dadas de baja por el sync de SysAcad");
        });
    }
}
