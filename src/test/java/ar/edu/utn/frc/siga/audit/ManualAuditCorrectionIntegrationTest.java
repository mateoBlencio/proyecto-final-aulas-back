package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SQL functions {@code auditoria_abrir_revision} / {@code auditoria_registrar} (V18) against a real
 * Postgres: a data fix made by hand must leave a revision that the audit API shows like any other.
 * Synthetic rows are inserted with {@code session_replication_role = replica} (set with SET LOCAL, so it
 * reverts on its own at commit) to avoid building every foreign key; everything created is removed afterwards.
 */
@DisplayName("Correcciones manuales auditadas por SQL (integración)")
class ManualAuditCorrectionIntegrationTest extends AbstractIntegrationTest {

    private static final String FIXER = "integration-test@frc.utn.edu.ar";
    private static final String PREFIX = "Corrección manual: ";
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final SettingKey KEY = SettingKey.EVENTS_HOURS_END;
    private static final AtomicLong IDS = new AtomicLong(9_000_000_000_000L + System.nanoTime() % 1_000_000L * 100);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private SettingsStore settingsStore;
    @Autowired
    private AuditedEntityRegistry registry;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<Integer> openedRevisions = new ArrayList<>();
    private final List<Long> seededOccurrences = new ArrayList<>();
    private final List<Long> seededEvents = new ArrayList<>();
    private final List<Long> seededRoles = new ArrayList<>();
    private String originalSetting;

    @AfterEach
    void cleanUp() {
        tx(() -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            for (int rev : openedRevisions) {
                for (String table : List.of("revinfo_resumen", "configuracion_aud", "usuario_rol_aud", "evento_academico_aud",
                        "evento_recurrente_aud", "evento_unico_aud", "ocurrencia_aud", "revinfo")) {
                    jdbc.update("DELETE FROM " + table + " WHERE rev = ?", rev);
                }
            }
            seededRoles.forEach(id -> jdbc.update("DELETE FROM usuario_rol WHERE id_usuario_rol = ?", id));
            seededOccurrences.forEach(id -> jdbc.update("DELETE FROM ocurrencia WHERE id_ocurrencia = ?", id));
            seededEvents.forEach(id -> {
                jdbc.update("DELETE FROM evento_recurrente WHERE id_evento_academico = ?", id);
                jdbc.update("DELETE FROM evento_unico WHERE id_evento_academico = ?", id);
                jdbc.update("DELETE FROM evento_academico WHERE id_evento_academico = ?", id);
            });
            return null;
        });
        jdbc.execute("DROP TABLE IF EXISTS manual_audit_probe_aud");
        jdbc.execute("DROP TABLE IF EXISTS manual_audit_probe");
        openedRevisions.clear();
        seededOccurrences.clear();
        seededEvents.clear();
        seededRoles.clear();
        if (originalSetting != null) {
            settingsStore.write(KEY, originalSetting);
            originalSetting = null;
        }
    }

    // ---- helpers ----

    private <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private void tx(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private int openRevision(String description) {
        int rev = jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)", Integer.class,
                description, FIXER);
        openedRevisions.add(rev);
        return rev;
    }

    private int register(int rev, String table, String id, int revtype) {
        return jdbc.queryForObject("SELECT auditoria_registrar(?, ?::text, ?::text, ?)", Integer.class,
                rev, table, id, revtype);
    }

    private int register(int rev, String table, long id, int revtype) {
        return jdbc.queryForObject("SELECT auditoria_registrar(?, ?::text, ?::bigint, ?)", Integer.class,
                rev, table, id, revtype);
    }

    private void seed(Consumer<JdbcTemplate> inserts) {
        tx(() -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            inserts.accept(jdbc);
        });
    }

    private long seedOccurrence() {
        long id = IDS.incrementAndGet();
        seededOccurrences.add(id);
        seed(j -> j.update("INSERT INTO ocurrencia (id_ocurrencia, id_evento_academico, fecha, estado) "
                + "VALUES (?, ?, ?, 'NEEDS_ROOM')", id, id, Date.valueOf(LocalDate.of(2031, 3, 4))));
        return id;
    }

    private long seedEvent(boolean recurring) {
        long id = IDS.incrementAndGet();
        seededEvents.add(id);
        seed(j -> {
            j.update("INSERT INTO evento_academico (id_evento_academico, cantidad_inscriptos, hora_inicio, "
                    + "duracion_minutos, tipo_evento) VALUES (?, 30, '08:00', 90, ?)",
                    id, recurring ? "RECURRING" : "UNIQUE_EVENT");
            if (recurring) {
                j.update("INSERT INTO evento_recurrente (id_evento_academico, dia_semana, fecha_inicio) "
                        + "VALUES (?, 'MONDAY', '2031-03-03')", id);
            } else {
                j.update("INSERT INTO evento_unico (id_evento_academico, fecha, tipo_actividad) "
                        + "VALUES (?, '2031-03-04', 'PARCIAL')", id);
            }
        });
        return id;
    }

    /** A revision shaped like the ones the app (or a script) could create, bypassing auditoria_abrir_revision. */
    private int insertRevision(String description, String actor) {
        int rev = jdbc.queryForObject("INSERT INTO revinfo (fecha_revision, usuario, tipo_actor, descripcion, operacion_id) "
                + "VALUES (now(), ?, ?, ?, ?) RETURNING rev", Integer.class, FIXER, actor, description,
                UUID.randomUUID().toString());
        openedRevisions.add(rev);
        return rev;
    }

    /** Runs a statement on one connection and returns the server notices it raised. */
    private List<String> notices(String sql) {
        return jdbc.execute((java.sql.Connection con) -> {
            try (Statement st = con.createStatement()) {
                st.execute(sql);
                List<String> found = new ArrayList<>();
                for (SQLWarning w = st.getWarnings(); w != null; w = w.getNextWarning()) {
                    found.add(w.getMessage());
                }
                return found;
            }
        });
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private Integer summaryCount(int rev, String table, int revtype) {
        List<Integer> found = jdbc.queryForList(
                "SELECT cantidad FROM revinfo_resumen WHERE rev = ? AND tabla_aud = ? AND revtype = ?",
                Integer.class, rev, table, revtype);
        return found.isEmpty() ? null : found.getFirst();
    }

    private long auditRows(int rev) {
        long total = 0;
        for (String table : List.of("configuracion_aud", "evento_academico_aud", "evento_recurrente_aud",
                "evento_unico_aud", "ocurrencia_aud")) {
            total += count("SELECT COUNT(*) FROM " + table + " WHERE rev = ?", rev);
        }
        return total;
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    // ---- 1. auditoria_abrir_revision ----

    @Test
    @DisplayName("abrir una revisión crea un revinfo HUMAN con usuario, prefijo en la descripción y operacion_id UUID")
    void openRevisionWritesHumanRevinfo() {
        int first = openRevision("merge de comisiones");
        int second = openRevision("otra corrección");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT usuario, tipo_actor, descripcion, operacion_id, operacion_padre_id FROM revinfo WHERE rev = ?",
                first);
        assertThat(row.get("usuario")).isEqualTo(FIXER);
        assertThat(row.get("tipo_actor")).isEqualTo("HUMAN");
        assertThat(row.get("descripcion")).isEqualTo(PREFIX + "merge de comisiones");
        assertThat(UUID.fromString((String) row.get("operacion_id"))).isNotNull();
        assertThat(row.get("operacion_padre_id")).isNull();
        assertThat(jdbc.queryForObject("SELECT operacion_id FROM revinfo WHERE rev = ?", String.class, second))
                .isNotEqualTo(row.get("operacion_id"));
    }

    @Test
    @DisplayName("abrir una revisión rechaza descripción nula y usuario nulo")
    void openRevisionRejectsNullArguments() {
        long before = count("SELECT COUNT(*) FROM revinfo");

        assertThatThrownBy(() -> jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)",
                Integer.class, null, FIXER)).hasMessageContaining("p_descripcion is required");
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)",
                Integer.class, "algo", null)).hasMessageContaining("p_usuario is required");

        assertThat(count("SELECT COUNT(*) FROM revinfo")).isEqualTo(before);
    }

    @ParameterizedTest(name = "usuario [{0}]")
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("abrir una revisión rechaza un usuario vacío o en blanco")
    void openRevisionRejectsBlankUser(String blank) {
        long before = count("SELECT COUNT(*) FROM revinfo");

        assertThatThrownBy(() -> jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)",
                Integer.class, "algo", blank)).hasMessageContaining("p_usuario is required");

        assertThat(count("SELECT COUNT(*) FROM revinfo")).isEqualTo(before);
    }

    @ParameterizedTest(name = "descripción [{0}]")
    @ValueSource(strings = {"", "  "})
    @DisplayName("abrir una revisión rechaza una descripción vacía o en blanco")
    void openRevisionRejectsBlankDescription(String blank) {
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)",
                Integer.class, blank, FIXER)).hasMessageContaining("p_descripcion is required");
    }

    // ---- 2. configuración: base + API ----

    @Test
    @DisplayName("corregir una configuración deja configuracion_aud MOD, el resumen, la entrada OPERATION HUMAN y el diff por campo")
    void settingCorrectionIsVisibleInTheAuditApi() throws Exception {
        originalSetting = settingsStore.getRaw(KEY);
        // The diff compares with the previous Envers revision, so one is created through the API first.
        mockMvc.perform(put("/v1/settings/{key}", KEY.getKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"22:13\"}"))
                .andExpect(status().isOk());

        int rev = tx(() -> {
            int opened = openRevision("horario de cierre mal cargado");
            jdbc.update("UPDATE configuracion SET valor = '22:14' WHERE clave = ?", KEY.getKey());
            assertThat(register(opened, "configuracion_aud", KEY.getKey(), 1)).isEqualTo(1);
            return opened;
        });

        Map<String, Object> aud = jdbc.queryForMap(
                "SELECT revtype, valor FROM configuracion_aud WHERE rev = ? AND clave = ?", rev, KEY.getKey());
        assertThat(((Number) aud.get("revtype")).intValue()).isEqualTo(1);
        assertThat(aud.get("valor")).isEqualTo("22:14");
        assertThat(summaryCount(rev, "configuracion_aud", 1)).isEqualTo(1);

        String operationId = jdbc.queryForObject("SELECT operacion_id FROM revinfo WHERE rev = ?", String.class, rev);
        JsonNode listing = json(get("/v1/audit").param("size", "50").param("user", FIXER));
        JsonNode entry = null;
        for (JsonNode candidate : listing.get("content")) {
            if (operationId.equals(candidate.path("operationId").asText(null))) {
                entry = candidate;
            }
        }
        assertThat(entry).as("entrada del registro con operationId %s", operationId).isNotNull();
        assertThat(entry.get("type").asText()).isEqualTo("OPERATION");
        assertThat(entry.get("actorType").asText()).isEqualTo("HUMAN");
        assertThat(entry.get("user").asText()).isEqualTo(FIXER);
        assertThat(entry.get("description").asText()).isEqualTo(PREFIX + "horario de cierre mal cargado");

        JsonNode detail = json(get("/v1/audit/revisions/{rev}", rev));
        assertThat(detail.get("content")).hasSize(1);
        JsonNode change = detail.get("content").get(0);
        assertThat(change.get("recordId").asText()).isEqualTo(KEY.getKey());
        assertThat(change.get("kind").asText()).isEqualTo("MODIFIED");
        assertThat(change.get("changes")).hasSize(1);
        assertThat(change.get("changes").get(0).get("field").asText()).isEqualTo("value");
        assertThat(change.get("changes").get(0).get("oldValue").asText()).isEqualTo("22:13");
        assertThat(change.get("changes").get(0).get("newValue").asText()).isEqualTo("22:14");
    }

    // ---- 3. jerarquía de eventos ----

    @Test
    @DisplayName("registrar un evento recurrente escribe evento_academico_aud y evento_recurrente_aud, y el resumen cuenta solo la raíz")
    void recurringEventWritesBothHierarchyTables() {
        long id = seedEvent(true);
        int rev = openRevision("evento recurrente");

        int written = register(rev, "evento_academico_aud", id, 0);

        assertThat(written).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT revtype, tipo_evento, cantidad_inscriptos FROM evento_academico_aud "
                + "WHERE rev = ? AND id_evento_academico = ?", rev, id))
                .containsEntry("tipo_evento", "RECURRING").containsEntry("cantidad_inscriptos", 30);
        assertThat(jdbc.queryForObject("SELECT dia_semana FROM evento_recurrente_aud WHERE rev = ? "
                + "AND id_evento_academico = ?", String.class, rev, id)).isEqualTo("MONDAY");
        assertThat(count("SELECT COUNT(*) FROM evento_unico_aud WHERE rev = ?", rev)).isZero();
        assertThat(summaryCount(rev, "evento_academico_aud", 0)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM revinfo_resumen WHERE rev = ?", rev)).isEqualTo(1);
    }

    @Test
    @DisplayName("registrar un evento único escribe evento_academico_aud y evento_unico_aud, y el resumen cuenta solo la raíz")
    void uniqueEventWritesBothHierarchyTables() {
        long id = seedEvent(false);
        int rev = openRevision("evento único");

        int written = register(rev, "evento_academico_aud", id, 0);

        assertThat(written).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT tipo_evento FROM evento_academico_aud WHERE rev = ?",
                String.class, rev)).isEqualTo("UNIQUE_EVENT");
        assertThat(jdbc.queryForObject("SELECT tipo_actividad FROM evento_unico_aud WHERE rev = ? "
                + "AND id_evento_academico = ?", String.class, rev, id)).isEqualTo("PARCIAL");
        assertThat(count("SELECT COUNT(*) FROM evento_recurrente_aud WHERE rev = ?", rev)).isZero();
        assertThat(summaryCount(rev, "evento_academico_aud", 0)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM revinfo_resumen WHERE rev = ?", rev)).isEqualTo(1);
    }

    // ---- 4. borrado ----

    @Test
    @DisplayName("registrar con revtype 2 antes del DELETE conserva los valores del último estado en la _aud")
    void registerBeforeDeleteKeepsLastState() {
        long id = seedOccurrence();
        int rev = openRevision("ocurrencia duplicada");

        tx(() -> {
            assertThat(register(rev, "ocurrencia_aud", id, 2)).isEqualTo(1);
            jdbc.update("DELETE FROM ocurrencia WHERE id_ocurrencia = ?", id);
        });

        assertThat(count("SELECT COUNT(*) FROM ocurrencia WHERE id_ocurrencia = ?", id)).isZero();
        Map<String, Object> aud = jdbc.queryForMap("SELECT revtype, fecha, estado, id_evento_academico "
                + "FROM ocurrencia_aud WHERE rev = ? AND id_ocurrencia = ?", rev, id);
        assertThat(((Number) aud.get("revtype")).intValue()).isEqualTo(2);
        assertThat(aud.get("fecha")).hasToString("2031-03-04");
        assertThat(aud.get("estado")).isEqualTo("NEEDS_ROOM");
        assertThat(((Number) aud.get("id_evento_academico")).longValue()).isEqualTo(id);
        assertThat(summaryCount(rev, "ocurrencia_aud", 2)).isEqualTo(1);
    }

    // ---- 5. errores ----

    @Test
    @DisplayName("registrar dos veces la misma fila en la revisión falla y no suma al resumen")
    void duplicateRowFailsWithoutTouchingTheSummary() {
        long id = seedOccurrence();
        int rev = openRevision("duplicado");
        register(rev, "ocurrencia_aud", id, 1);

        assertThatThrownBy(() -> register(rev, "ocurrencia_aud", id, 1)).isInstanceOf(DuplicateKeyException.class);

        assertThat(count("SELECT COUNT(*) FROM ocurrencia_aud WHERE rev = ?", rev)).isEqualTo(1);
        assertThat(summaryCount(rev, "ocurrencia_aud", 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("registrar una fila inexistente falla y no deja filas _aud ni resumen")
    void missingRowFails() {
        int rev = openRevision("fila inexistente");

        assertThatThrownBy(() -> register(rev, "configuracion_aud", "no.existe", 1))
                .hasMessageContaining("has no row with id no.existe");

        assertThat(auditRows(rev)).isZero();
        assertThat(count("SELECT COUNT(*) FROM revinfo_resumen WHERE rev = ?", rev)).isZero();
    }

    @Test
    @DisplayName("registrar en una tabla inexistente falla")
    void missingTableFails() {
        int rev = openRevision("tabla inexistente");

        assertThatThrownBy(() -> register(rev, "inexistente_aud", "1", 1)).hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("un nombre que no termina en _aud se rechaza")
    void nonAuditTableNameFails() {
        int rev = openRevision("tabla base");

        assertThatThrownBy(() -> register(rev, "configuracion", "x", 1)).hasMessageContaining("is not an _aud table");
    }

    @ParameterizedTest(name = "tabla [{0}]")
    @ValueSource(strings = {
            "revinfo; DROP TABLE revinfo CASCADE; --_aud",
            "configuracion_aud WHERE true; DELETE FROM revinfo; --_aud",
            "configuracion_aud\"; DELETE FROM revinfo; --_aud"})
    @DisplayName("un intento de inyección en el nombre de tabla falla y revinfo queda intacta")
    void tableNameInjectionIsRejected(String payload) {
        int rev = openRevision("inyección");
        long revisionsBefore = count("SELECT COUNT(*) FROM revinfo");

        assertThatThrownBy(() -> register(rev, payload, "x", 1)).isInstanceOf(DataAccessException.class);

        assertThat(count("SELECT COUNT(*) FROM revinfo")).isEqualTo(revisionsBefore);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.revinfo') IS NOT NULL", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("un intento de inyección en el id se trata como valor: no copia filas ajenas")
    void idInjectionIsTreatedAsValue() {
        originalSetting = settingsStore.getRaw(KEY);
        settingsStore.write(KEY, "22:15");
        int rev = openRevision("inyección en id");

        assertThatThrownBy(() -> register(rev, "configuracion_aud", "x' OR '1'='1", 1))
                .hasMessageContaining("has no row with id");

        assertThat(auditRows(rev)).isZero();
    }

    @ParameterizedTest(name = "revtype {0}")
    @ValueSource(ints = {-1, 3, 5})
    @DisplayName("un revtype fuera de 0, 1, 2 se rechaza")
    void invalidRevtypeFails(int revtype) {
        long id = seedOccurrence();
        int rev = openRevision("revtype inválido");

        assertThatThrownBy(() -> register(rev, "ocurrencia_aud", id, revtype))
                .hasMessageContaining("p_revtype must be 0, 1 or 2");

        assertThat(auditRows(rev)).isZero();
    }

    @ParameterizedTest(name = "tabla [{0}]")
    @ValueSource(strings = {"evento_recurrente_aud", "evento_unico_aud"})
    @DisplayName("las tablas de subclase se rechazan: se registra evento_academico_aud")
    void subclassTablesAreRejected(String table) {
        long id = seedEvent(true);
        int rev = openRevision("subclase");

        assertThatThrownBy(() -> register(rev, table, id, 1))
                .hasMessageContaining("register evento_academico_aud");

        assertThat(auditRows(rev)).isZero();
    }

    @Test
    @DisplayName("una revisión inexistente se rechaza")
    void missingRevisionFails() {
        long id = seedOccurrence();
        int missing = jdbc.queryForObject("SELECT COALESCE(MAX(rev), 0) + 1000000 FROM revinfo", Integer.class);

        assertThatThrownBy(() -> register(missing, "ocurrencia_aud", id, 1))
                .hasMessageContaining("revision " + missing + " does not exist");

        assertThat(count("SELECT COUNT(*) FROM ocurrencia_aud WHERE rev = ?", missing)).isZero();
    }

    @Test
    @DisplayName("la sobrecarga numérica registra por id_ocurrencia y la de texto por clave")
    void numericAndTextOverloadsResolveByKeyType() {
        long id = seedOccurrence();
        long other = seedOccurrence();
        originalSetting = settingsStore.getRaw(KEY);
        settingsStore.write(KEY, "22:16");
        int rev = openRevision("sobrecargas");

        assertThat(register(rev, "ocurrencia_aud", id, 1)).isEqualTo(1);
        assertThat(register(rev, "ocurrencia_aud", String.valueOf(other), 1)).isEqualTo(1);
        assertThat(register(rev, "configuracion_aud", KEY.getKey(), 1)).isEqualTo(1);

        assertThat(count("SELECT COUNT(*) FROM ocurrencia_aud WHERE rev = ? AND id_ocurrencia IN (?, ?)",
                rev, id, other)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM configuracion_aud WHERE rev = ? AND clave = ?", rev, KEY.getKey()))
                .isEqualTo(1);
        // A numeric id on a text-keyed table is compared as text and finds nothing.
        assertThatThrownBy(() -> register(rev, "configuracion_aud", 7L, 1)).hasMessageContaining("has no row with id 7");
        // A non-numeric id on a numeric-keyed table is rejected with its own message, before writing anything.
        assertThatThrownBy(() -> register(rev, "ocurrencia_aud", "abc", 1))
                .hasMessageContaining("ocurrencia id 'abc' is not a valid bigint");
    }

    // ---- 6. upsert del resumen ----

    @Test
    @DisplayName("varias filas de la misma tabla y revtype acumulan cantidad, y otro revtype queda en su propia fila")
    void summaryAccumulatesPerTableAndRevtype() {
        long a = seedOccurrence();
        long b = seedOccurrence();
        long c = seedOccurrence();
        long d = seedOccurrence();
        int rev = openRevision("acumulación");

        register(rev, "ocurrencia_aud", a, 1);
        register(rev, "ocurrencia_aud", b, 1);
        register(rev, "ocurrencia_aud", c, 1);
        register(rev, "ocurrencia_aud", d, 0);

        assertThat(summaryCount(rev, "ocurrencia_aud", 1)).isEqualTo(3);
        assertThat(summaryCount(rev, "ocurrencia_aud", 0)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM revinfo_resumen WHERE rev = ?", rev)).isEqualTo(2);
    }

    // ---- 7. hora ----

    @Test
    @DisplayName("fecha_revision es la hora de Buenos Aires aunque la sesión de Postgres esté en UTC")
    void revisionDateIsBuenosAiresTimeWithUtcSession() {
        AtomicInteger revHolder = new AtomicInteger();
        LocalDateTime[] stored = new LocalDateTime[1];
        String[] sessionZone = new String[1];

        LocalDateTime before = ZonedDateTime.now(BUENOS_AIRES).toLocalDateTime();
        tx(() -> {
            jdbc.execute("SET LOCAL TIME ZONE 'UTC'");
            sessionZone[0] = jdbc.queryForObject("SHOW TIME ZONE", String.class);
            revHolder.set(openRevision("hora"));
            stored[0] = jdbc.queryForObject("SELECT fecha_revision FROM revinfo WHERE rev = ?",
                    LocalDateTime.class, revHolder.get());
        });
        LocalDateTime after = ZonedDateTime.now(BUENOS_AIRES).toLocalDateTime();

        assertThat(sessionZone[0]).isEqualTo("UTC");
        assertThat(stored[0]).isBetween(before.minusSeconds(5), after.plusSeconds(5));
        // Guard against a UTC reading that would be 3 hours ahead of Buenos Aires.
        assertThat(Duration.between(stored[0], after).abs()).isLessThan(Duration.ofMinutes(1));
    }

    // ---- 8. atomicidad ----

    @Test
    @DisplayName("si auditoria_registrar falla dentro de una transacción, el rollback no deja revisión, filas _aud ni resumen")
    void failureRollsBackRevisionRowsAndSummary() {
        long id = seedOccurrence();
        AtomicInteger revHolder = new AtomicInteger();

        assertThatThrownBy(() -> tx(() -> {
            int opened = openRevision("atomicidad");
            revHolder.set(opened);
            register(opened, "ocurrencia_aud", id, 1);
            assertThat(count("SELECT COUNT(*) FROM ocurrencia_aud WHERE rev = ?", opened)).isEqualTo(1);
            register(opened, "ocurrencia_aud", id + 1_000_000, 1);
        })).isInstanceOf(DataAccessException.class).hasMessageContaining("has no row with id");

        int rev = revHolder.get();
        assertThat(rev).isPositive();
        assertThat(count("SELECT COUNT(*) FROM revinfo WHERE rev = ?", rev)).isZero();
        assertThat(count("SELECT COUNT(*) FROM ocurrencia_aud WHERE rev = ?", rev)).isZero();
        assertThat(count("SELECT COUNT(*) FROM revinfo_resumen WHERE rev = ?", rev)).isZero();
    }

    // ---- 9. consistencia con el resumen de F8 ----

    @Test
    @DisplayName("después de una corrección, revinfo_resumen coincide con el GROUP BY rev, revtype de las _aud raíz")
    void summaryMatchesRootAuditTablesGroupBy() {
        long occurrenceA = seedOccurrence();
        long occurrenceB = seedOccurrence();
        long recurring = seedEvent(true);
        long unique = seedEvent(false);
        originalSetting = settingsStore.getRaw(KEY);
        settingsStore.write(KEY, "22:17");
        int rev = openRevision("consistencia");

        tx(() -> {
            register(rev, "ocurrencia_aud", occurrenceA, 1);
            register(rev, "ocurrencia_aud", occurrenceB, 0);
            register(rev, "evento_academico_aud", recurring, 0);
            register(rev, "evento_academico_aud", unique, 0);
            register(rev, "configuracion_aud", KEY.getKey(), 1);
        });

        TreeSet<String> expected = new TreeSet<>();
        for (AuditedEntity entity : registry.all()) {
            jdbc.query("SELECT revtype, COUNT(*) AS n FROM " + entity.auditTable()
                            + " WHERE rev = ? GROUP BY revtype", rs -> {
                        expected.add(entity.auditTable() + ":" + rs.getInt("revtype") + ":" + rs.getLong("n"));
                    }, rev);
        }
        TreeSet<String> summary = new TreeSet<>();
        jdbc.query("SELECT tabla_aud, revtype, cantidad FROM revinfo_resumen WHERE rev = ?", rs -> {
            summary.add(rs.getString("tabla_aud") + ":" + rs.getInt("revtype") + ":" + rs.getLong("cantidad"));
        }, rev);

        assertThat(expected).containsExactlyInAnyOrder(
                "ocurrencia_aud:1:1", "ocurrencia_aud:0:1", "evento_academico_aud:0:2", "configuracion_aud:1:1");
        assertThat(summary).isEqualTo(expected);
    }

    // ---- 10. restricción a correcciones manuales ----

    @Test
    @DisplayName("registrar sobre una revisión creada por la app (PUT de settings) falla y no escribe nada")
    void registerOnAppRevisionFails() throws Exception {
        originalSetting = settingsStore.getRaw(KEY);
        mockMvc.perform(put("/v1/settings/{key}", KEY.getKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"22:18\"}"))
                .andExpect(status().isOk());
        int appRev = jdbc.queryForObject("SELECT MAX(rev) FROM configuracion_aud WHERE clave = ?", Integer.class,
                KEY.getKey());
        long audBefore = count("SELECT COUNT(*) FROM configuracion_aud WHERE rev = ?", appRev);
        long summaryBefore = count("SELECT COUNT(*) FROM revinfo_resumen WHERE rev = ?", appRev);

        assertThatThrownBy(() -> register(appRev, "ocurrencia_aud", seedOccurrence(), 1))
                .hasMessageContaining("is not a manual correction");
        assertThatThrownBy(() -> register(appRev, "configuracion_aud", KEY.getKey(), 1))
                .hasMessageContaining("is not a manual correction");

        assertThat(count("SELECT COUNT(*) FROM configuracion_aud WHERE rev = ?", appRev)).isEqualTo(audBefore);
        assertThat(count("SELECT COUNT(*) FROM revinfo_resumen WHERE rev = ?", appRev)).isEqualTo(summaryBefore);
        assertThat(count("SELECT COUNT(*) FROM ocurrencia_aud WHERE rev = ?", appRev)).isZero();
    }

    @Test
    @DisplayName("solo una revisión HUMAN con el prefijo de corrección manual acepta registros")
    void registerRequiresPrefixAndHumanActor() {
        long id = seedOccurrence();
        int systemWithPrefix = insertRevision(PREFIX + "x", "SYSTEM");
        int humanWithoutPrefix = insertRevision("Modificación de configuración", "HUMAN");
        int humanWithPrefix = insertRevision(PREFIX + "x", "HUMAN");

        assertThatThrownBy(() -> register(systemWithPrefix, "ocurrencia_aud", id, 1))
                .hasMessageContaining("is not a manual correction");
        assertThatThrownBy(() -> register(humanWithoutPrefix, "ocurrencia_aud", id, 1))
                .hasMessageContaining("is not a manual correction");
        assertThat(register(humanWithPrefix, "ocurrencia_aud", id, 1)).isEqualTo(1);
    }

    // ---- 11. hora de inserción ----

    @Test
    @DisplayName("fecha_revision refleja la hora de la inserción y no la del BEGIN de la transacción")
    void revisionDateIsInsertionTimeNotTransactionStart() {
        LocalDateTime[] txStart = new LocalDateTime[1];
        LocalDateTime[] stored = new LocalDateTime[1];

        tx(() -> {
            txStart[0] = jdbc.queryForObject("SELECT now() AT TIME ZONE 'America/Argentina/Buenos_Aires'",
                    LocalDateTime.class);
            jdbc.execute("SELECT pg_sleep(1.5)");
            int rev = openRevision("hora de inserción");
            stored[0] = jdbc.queryForObject("SELECT fecha_revision FROM revinfo WHERE rev = ?",
                    LocalDateTime.class, rev);
        });

        assertThat(Duration.between(txStart[0], stored[0])).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
    }

    // ---- 12. largo máximo ----

    @Test
    @DisplayName("la descripción de 236 caracteres entra y completa los 255 del campo; 237 se rechaza con mensaje propio")
    void descriptionLengthBoundary() {
        int rev = openRevision("d".repeat(236));
        assertThat(jdbc.queryForObject("SELECT length(descripcion) FROM revinfo WHERE rev = ?", Integer.class, rev))
                .isEqualTo(255);

        long before = count("SELECT COUNT(*) FROM revinfo");
        assertThatThrownBy(() -> openRevision("d".repeat(237)))
                .hasMessageContaining("p_descripcion has 237 chars, the maximum is 236");
        assertThat(count("SELECT COUNT(*) FROM revinfo")).isEqualTo(before);
    }

    @Test
    @DisplayName("el largo del usuario se valida antes de buscarlo: 255 llega a la búsqueda y 256 se rechaza por largo")
    void userLengthBoundary() {
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)",
                Integer.class, "largo", "u".repeat(255)))
                .hasMessageContaining("is not a registered usuario.correo");
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)",
                Integer.class, "largo", "u".repeat(256)))
                .hasMessageContaining("p_usuario has 256 chars, the maximum is 255");
    }

    // ---- 13. aviso de columnas sin contraparte ----

    @Test
    @DisplayName("avisa con NOTICE las columnas de la _aud sin contraparte en la tabla base, y no avisa si no hay")
    void noticeListsAuditOnlyColumns() {
        jdbc.execute("CREATE TABLE manual_audit_probe (id bigint PRIMARY KEY, name text)");
        jdbc.execute("CREATE TABLE manual_audit_probe_aud (id bigint NOT NULL, rev integer NOT NULL, "
                + "revtype smallint, name text, audit_only_column text, PRIMARY KEY (id, rev))");
        jdbc.update("INSERT INTO manual_audit_probe VALUES (1, 'a')");
        int rev = openRevision("aviso");
        long occurrence = seedOccurrence();

        List<String> withExtra = notices("SELECT auditoria_registrar(" + rev + ", 'manual_audit_probe_aud', '1', 0)");
        List<String> withoutExtra = notices("SELECT auditoria_registrar(" + rev + ", 'ocurrencia_aud', '"
                + occurrence + "', 0)");

        assertThat(withExtra).singleElement().asString()
                .contains("manual_audit_probe_aud").contains("audit_only_column");
        assertThat(withoutExtra).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM manual_audit_probe_aud WHERE rev = ?", rev)).isEqualTo(1);
    }

    // ---- 14. usuario_rol_aud (clave simple id_usuario_rol) ----

    @Test
    @DisplayName("usuario_rol_aud se registra por id_usuario_rol, su clave primaria simple")
    void userRoleAuditUsesSinglePrimaryKey() {
        long id = IDS.incrementAndGet();
        seededRoles.add(id);
        seed(j -> j.update("INSERT INTO usuario_rol (id_usuario_rol, id_usuario, rol, tipo_alcance) "
                + "VALUES (?, ?, 'SUBSECRETARIA', 'GLOBAL')", id, id));
        int rev = openRevision("rol");

        assertThat(register(rev, "usuario_rol_aud", id, 0)).isEqualTo(1);

        Map<String, Object> aud = jdbc.queryForMap("SELECT revtype, id_usuario, rol, tipo_alcance "
                + "FROM usuario_rol_aud WHERE rev = ? AND id_usuario_rol = ?", rev, id);
        assertThat(aud).containsEntry("rol", "SUBSECRETARIA").containsEntry("tipo_alcance", "GLOBAL");
        assertThat(((Number) aud.get("id_usuario")).longValue()).isEqualTo(id);
        assertThat(summaryCount(rev, "usuario_rol_aud", 0)).isEqualTo(1);
    }

    // ---- 15. usuario_bd ----

    @Test
    @DisplayName("usuario_bd guarda el usuario de la conexión que abrió la revisión")
    void openRevisionStoresDatabaseUser() {
        String[] expected = new String[1];
        int[] rev = new int[1];
        tx(() -> {
            String sessionUser = jdbc.queryForObject("SELECT session_user", String.class);
            String currentUser = jdbc.queryForObject("SELECT current_user", String.class);
            expected[0] = sessionUser.equals(currentUser) ? sessionUser : sessionUser + " as " + currentUser;
            rev[0] = openRevision("usuario de base");
        });

        assertThat(expected[0]).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT usuario_bd FROM revinfo WHERE rev = ?", String.class, rev[0]))
                .isEqualTo(expected[0]);
    }

    @Test
    @DisplayName("una revisión creada por la app deja usuario_bd en null")
    void appRevisionHasNullDatabaseUser() throws Exception {
        originalSetting = settingsStore.getRaw(KEY);
        mockMvc.perform(put("/v1/settings/{key}", KEY.getKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"22:19\"}"))
                .andExpect(status().isOk());
        int appRev = jdbc.queryForObject("SELECT MAX(rev) FROM configuracion_aud WHERE clave = ?", Integer.class,
                KEY.getKey());

        assertThat(jdbc.queryForObject("SELECT usuario_bd FROM revinfo WHERE rev = ?", String.class, appRev))
                .isNull();
    }

    @Test
    @DisplayName("usuario_bd no aparece en el JSON de GET /v1/audit ni en el de /revisions/{rev}")
    void databaseUserIsNotExposedByTheApi() throws Exception {
        int rev = openRevision("no exponer usuario de base");
        String databaseUser = jdbc.queryForObject("SELECT usuario_bd FROM revinfo WHERE rev = ?", String.class, rev);
        assertThat(databaseUser).isNotBlank();
        long id = seedOccurrence();
        register(rev, "ocurrencia_aud", id, 1);

        String listing = mockMvc.perform(get("/v1/audit").param("size", "50").param("user", FIXER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String detail = mockMvc.perform(get("/v1/audit/revisions/{rev}", rev))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(listing).contains(PREFIX + "no exponer usuario de base");
        assertThat(detail).contains("\"recordId\":\"" + id + "\"");
        for (String body : List.of(listing, detail)) {
            assertThat(body).doesNotContain("usuario_bd").doesNotContain("usuarioBd").doesNotContain("databaseUser")
                    .doesNotContain("\"" + databaseUser + "\"");
        }
    }

    // ---- 16. el usuario debe existir en usuario.correo ----

    @Test
    @DisplayName("un usuario que no existe en usuario.correo falla y no crea la revisión")
    void unknownUserFails() {
        long before = count("SELECT COUNT(*) FROM revinfo");

        assertThatThrownBy(() -> jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)",
                Integer.class, "algo", "nadie-" + System.nanoTime() + "@frc.utn.edu.ar"))
                .hasMessageContaining("is not a registered usuario.correo");

        assertThat(count("SELECT COUNT(*) FROM revinfo")).isEqualTo(before);
    }

    @Test
    @DisplayName("un correo con otras mayúsculas pasa y revinfo.usuario guarda la forma canónica de la tabla")
    void userMatchIsCaseInsensitiveAndStoresCanonicalForm() {
        String shouting = FIXER.toUpperCase();
        assertThat(shouting).isNotEqualTo(FIXER);

        int rev = jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)", Integer.class,
                "mayúsculas", shouting);
        openedRevisions.add(rev);

        assertThat(jdbc.queryForObject("SELECT usuario FROM revinfo WHERE rev = ?", String.class, rev))
                .isEqualTo(FIXER);
    }

    @Test
    @DisplayName("un usuario deshabilitado puede abrir una revisión")
    void disabledUserIsAccepted() {
        long id = IDS.incrementAndGet();
        String email = "disabled-" + id + "@frc.utn.edu.ar";
        jdbc.update("INSERT INTO usuario (id_usuario, correo, habilitado, password_hash, nombre, apellido) "
                + "VALUES (?, ?, false, 'x', 'Baja', 'Test')", id, email);
        try {
            int rev = jdbc.queryForObject("SELECT auditoria_abrir_revision(?::text, ?::text)", Integer.class,
                    "usuario deshabilitado", email);
            openedRevisions.add(rev);

            assertThat(jdbc.queryForObject("SELECT usuario FROM revinfo WHERE rev = ?", String.class, rev))
                    .isEqualTo(email);
        } finally {
            cleanUp();
            jdbc.update("DELETE FROM usuario WHERE id_usuario = ?", id);
        }
    }
}
