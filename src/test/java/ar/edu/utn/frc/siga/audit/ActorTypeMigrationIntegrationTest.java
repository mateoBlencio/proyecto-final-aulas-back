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

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Backfill of V14 on its own database: Flyway migrates to V13, the test seeds historical revisions,
 * and then migrates to V14. The shared Spring context already ran every migration, so the
 * {@code UPDATE} of V14 can't be observed there.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V14 tipo_actor en revinfo (integración)")
class ActorTypeMigrationIntegrationTest {

    private static final int REV_PUBLIC_FORM_CREATE = 1;
    private static final int REV_MIXED_WITH_CREATE = 2;
    private static final int REV_ROOM_REQUEST_MODIFY_ONLY = 3;
    private static final int REV_NO_AUDIT_ROWS = 4;
    private static final int REV_WITH_USER_NO_ROWS = 5;
    private static final int REV_WITH_USER_ROOM_REQUEST_CREATE = 6;

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateToV13SeedAndMigrateToV14() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("13").load().migrate();
        seedHistoricalRevisions();
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static void seedHistoricalRevisions() {
        revision(REV_PUBLIC_FORM_CREATE, null);
        roomRequestAudit(REV_PUBLIC_FORM_CREATE, 101, 0);

        revision(REV_MIXED_WITH_CREATE, null);
        roomRequestAudit(REV_MIXED_WITH_CREATE, 102, 0);
        roomRequestAudit(REV_MIXED_WITH_CREATE, 103, 1);

        revision(REV_ROOM_REQUEST_MODIFY_ONLY, null);
        roomRequestAudit(REV_ROOM_REQUEST_MODIFY_ONLY, 104, 1);

        revision(REV_NO_AUDIT_ROWS, null);

        revision(REV_WITH_USER_NO_ROWS, "docente@frc.utn.edu.ar");

        revision(REV_WITH_USER_ROOM_REQUEST_CREATE, "docente@frc.utn.edu.ar");
        roomRequestAudit(REV_WITH_USER_ROOM_REQUEST_CREATE, 105, 0);
    }

    private static void revision(int rev, String user) {
        jdbc.update("INSERT INTO revinfo (rev, fecha_revision, usuario) VALUES (?, now(), ?)", rev, user);
    }

    private static void roomRequestAudit(int rev, long requestId, int revtype) {
        jdbc.update("INSERT INTO solicitud_aula_aud (rev, id_solicitud, revtype) VALUES (?, ?, ?)",
                rev, requestId, revtype);
    }

    private static Map<Integer, String> actorTypes() {
        return jdbc.queryForList("SELECT rev, tipo_actor FROM revinfo").stream()
                .collect(Collectors.toMap(row -> (Integer) row.get("rev"), row -> (String) row.get("tipo_actor")));
    }

    @Test
    @DisplayName("sin usuario y con alta de solicitud de aula (formulario público): HUMAN")
    void anonymousRoomRequestCreation_isHuman() {
        assertThat(actorTypes()).containsEntry(REV_PUBLIC_FORM_CREATE, "HUMAN");
    }

    @Test
    @DisplayName("sin usuario y con un alta de solicitud junto a otras filas: HUMAN")
    void anonymousRevisionWithOneRoomRequestCreation_isHuman() {
        assertThat(actorTypes()).containsEntry(REV_MIXED_WITH_CREATE, "HUMAN");
    }

    @Test
    @DisplayName("sin usuario y con solo modificaciones de solicitud de aula (revtype 1): SYSTEM")
    void anonymousRoomRequestModificationOnly_isSystem() {
        assertThat(actorTypes()).containsEntry(REV_ROOM_REQUEST_MODIFY_ONLY, "SYSTEM");
    }

    @Test
    @DisplayName("sin usuario y sin filas de solicitud de aula: SYSTEM")
    void anonymousRevisionWithoutRoomRequestRows_isSystem() {
        assertThat(actorTypes()).containsEntry(REV_NO_AUDIT_ROWS, "SYSTEM");
    }

    @Test
    @DisplayName("con usuario queda HUMAN, tenga o no un alta de solicitud")
    void revisionWithUser_isHuman() {
        assertThat(actorTypes())
                .containsEntry(REV_WITH_USER_NO_ROWS, "HUMAN")
                .containsEntry(REV_WITH_USER_ROOM_REQUEST_CREATE, "HUMAN");
    }

    @Test
    @DisplayName("la restricción rechaza valores distintos de HUMAN y SYSTEM")
    void checkConstraintRejectsUnknownValue() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO revinfo (rev, fecha_revision, usuario, tipo_actor) VALUES (99, now(), NULL, 'OTRO')"))
                .hasMessageContaining("revinfo_tipo_actor_check");
    }
}
