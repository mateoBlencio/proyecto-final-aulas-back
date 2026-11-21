package ar.edu.utn.frc.siga.events;

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

/**
 * V71 on its own database: Flyway migrates to V70, the test seeds events and occurrences, and then migrates
 * to the latest. The shared Spring context already ran every migration, so the data rewrite can't be observed there.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V71 evento académico habilitado_siga (integración)")
class EventSigaEnabledMigrationIntegrationTest {

    private static final String TODAY = "(now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date";

    private static final long SYNC_DOWN = 1;
    private static final long MANUAL = 2;
    private static final long SYNC_ENABLED = 3;

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void migrateToV70SeedAndMigrateToV71() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("70").load().migrate();
        seed();
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    /**
     * Event 1: synced by SysAcad and already off there (hash_raw set, habilitado_sysacad=false), so the
     * migration must turn it off in SIGA. Its occurrences cover every branch of the cleanup.
     * Event 2: loaded by hand (hash_raw null, habilitado_sysacad=false), must stay on with every occurrence.
     * Event 3: synced and still on in SysAcad, must stay on.
     */
    private static void seed() {
        jdbc.update("INSERT INTO edificio (id_edificio, nombre) VALUES (1, 'Central')");
        jdbc.update("INSERT INTO tipo_aula (id_tipo_aula, descripcion) VALUES (1, 'Común')");
        jdbc.update("INSERT INTO aula (id_aula, id_edificio, id_tipo_aula, capacidad, numero) VALUES (1, 1, 1, 30, 101)");
        jdbc.update("INSERT INTO solicitud_aula (id_solicitud, tipo_solicitud, ambito, docente_nombre, docente_email, "
                + "docente_telefono) VALUES (1, 'OTHER', 'GRADO', 'Docente', 'docente@frc.utn.edu.ar', '351')");
        jdbc.update("INSERT INTO solicitud_aula_item (id_item, id_solicitud, orden) VALUES (1, 1, 1)");

        event(SYNC_DOWN, "hash", false);
        event(MANUAL, null, false);
        event(SYNC_ENABLED, "hash", true);

        // Event 1: future without allocation or request (30 days, today), allocated future, requested future,
        // yesterday and 30 days ago.
        occurrence(101, SYNC_DOWN, 30);
        occurrence(102, SYNC_DOWN, 0);
        occurrence(103, SYNC_DOWN, 31);
        occurrence(104, SYNC_DOWN, 32);
        occurrence(105, SYNC_DOWN, -1);
        occurrence(106, SYNC_DOWN, -30);
        jdbc.update("INSERT INTO asignacion_aula (id_asignacion, id_aula, id_ocurrencia) VALUES (1, 1, 103)");
        jdbc.update("INSERT INTO solicitud_item_asignacion (id_item, id_ocurrencia, id_aula, orden) VALUES (1, 104, 1, 1)");

        occurrence(201, MANUAL, 30);
        occurrence(202, MANUAL, 0);
        occurrence(203, MANUAL, -30);

        occurrence(301, SYNC_ENABLED, 30);
        occurrence(302, SYNC_ENABLED, -30);
    }

    private static void event(long id, String hashRaw, boolean sysacadEnabled) {
        jdbc.update("INSERT INTO evento_academico (id_evento_academico, cantidad_inscriptos, hora_inicio, duracion_minutos, "
                + "tipo_evento, hash_raw, habilitado_sysacad) VALUES (?, 30, '08:00', 90, 'RECURRING', ?, ?)",
                id, hashRaw, sysacadEnabled);
    }

    private static void occurrence(long id, long eventId, int daysFromToday) {
        jdbc.update("INSERT INTO ocurrencia (id_ocurrencia, id_evento_academico, fecha) VALUES (?, ?, " + TODAY
                + " + ?)", id, eventId, daysFromToday);
    }

    private static boolean sigaEnabled(long eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT habilitado_siga FROM evento_academico WHERE id_evento_academico = ?", Boolean.class, eventId));
    }

    private static boolean sysacadEnabled(long eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT habilitado_sysacad FROM evento_academico WHERE id_evento_academico = ?", Boolean.class, eventId));
    }

    private static List<Long> occurrenceIds(long eventId) {
        return jdbc.queryForList("SELECT id_ocurrencia FROM ocurrencia WHERE id_evento_academico = ? ORDER BY 1",
                Long.class, eventId);
    }

    @Test
    @DisplayName("el evento sync dado de baja (hash_raw no nulo, habilitado_sysacad=false) queda con habilitado_siga=false y habilitado_sysacad sin cambio")
    void syncedEventOffInSysacadIsDisabledInSiga() {
        assertThat(sigaEnabled(SYNC_DOWN)).isFalse();
        assertThat(sysacadEnabled(SYNC_DOWN)).isFalse();
    }

    @Test
    @DisplayName("el evento manual (hash_raw nulo, habilitado_sysacad=false) queda con habilitado_siga=true y habilitado_sysacad sin cambio")
    void manualEventStaysEnabledInSiga() {
        assertThat(sigaEnabled(MANUAL)).isTrue();
        assertThat(sysacadEnabled(MANUAL)).isFalse();
    }

    @Test
    @DisplayName("el evento sync con habilitado_sysacad=true queda con habilitado_siga=true")
    void syncedEventOnInSysacadStaysEnabledInSiga() {
        assertThat(sigaEnabled(SYNC_ENABLED)).isTrue();
        assertThat(sysacadEnabled(SYNC_ENABLED)).isTrue();
    }

    @Test
    @DisplayName("el evento dado de baja pierde sus ocurrencias futuras (hoy incluido) sin asignación ni pedido")
    void disabledEventLosesFutureOccurrencesWithoutAllocationOrRequest() {
        assertThat(occurrenceIds(SYNC_DOWN)).doesNotContain(101L, 102L);
    }

    @Test
    @DisplayName("el evento dado de baja conserva la futura con asignación_aula, la futura con solicitud_item_asignacion y las pasadas (ayer incluido)")
    void disabledEventKeepsAllocatedRequestedAndPastOccurrences() {
        assertThat(occurrenceIds(SYNC_DOWN)).containsExactly(103L, 104L, 105L, 106L);
    }

    @Test
    @DisplayName("los eventos que siguen habilitados conservan todas sus ocurrencias")
    void enabledEventsKeepAllOccurrences() {
        assertThat(occurrenceIds(MANUAL)).containsExactly(201L, 202L, 203L);
        assertThat(occurrenceIds(SYNC_ENABLED)).containsExactly(301L, 302L);
    }

    @Test
    @DisplayName("la asignación y el pedido de las ocurrencias conservadas siguen intactos")
    void keptOccurrencesKeepTheirAllocationAndRequest() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asignacion_aula WHERE id_ocurrencia = 103", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitud_item_asignacion WHERE id_ocurrencia = 104",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("un evento insertado sin indicar la columna nace con habilitado_siga=true")
    void newEventDefaultsToEnabledInSiga() {
        jdbc.update("INSERT INTO evento_academico (id_evento_academico, cantidad_inscriptos, hora_inicio, duracion_minutos, "
                + "tipo_evento) VALUES (99, 10, '08:00', 60, 'UNIQUE_EVENT')");

        assertThat(sigaEnabled(99)).isTrue();
    }
}
