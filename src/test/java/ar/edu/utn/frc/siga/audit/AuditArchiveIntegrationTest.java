package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.AuditArchiveFixture.Scenario;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveOutcome;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveResultDto;
import ar.edu.utn.frc.siga.audit.internal.AuditArchiveScheduler;
import ar.edu.utn.frc.siga.audit.service.AuditArchiveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Archive scenario R1 to R8 of the plan (see {@link AuditArchiveFixture}), with today = 01/10/2004 and cutoff
 * 03/03/2003. Commits are real (no {@code @Transactional}): the archive runs one transaction per batch.
 */
@DisplayName("Archivado de auditoría (integración)")
class AuditArchiveIntegrationTest extends AbstractIntegrationTest {

    private static final String LISTING_RANGE_FROM = "2001-01-01";
    private static final String LISTING_RANGE_TO = "2003-12-31";

    @Autowired
    private AuditArchiveService archiveService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ApplicationContext applicationContext;

    private AuditArchiveFixture fixture;
    private Scenario s;

    @BeforeEach
    void seed() {
        fixture = new AuditArchiveFixture(jdbc);
        fixture.begin();
        fixture.seedPeriods();
        s = fixture.seedScenario();
    }

    @AfterEach
    void cleanUp() {
        fixture.end();
    }

    /** Runs the archive as the cron would: no request and no session, so the revisions come out as SYSTEM. */
    private AuditArchiveResultDto archiveAsSystem(LocalDate today) {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        return archiveService.archive(today);
    }

    private AuditArchiveResultDto archiveAsSystem() {
        return archiveAsSystem(AuditArchiveFixture.TODAY);
    }

    private List<Map<String, Object>> rows(String qualifiedTable, String idColumn, List<Integer> revisions) {
        String in = String.join(",", revisions.stream().map(String::valueOf).toList());
        return jdbc.queryForList("SELECT * FROM " + qualifiedTable + " WHERE rev IN (" + in + ") ORDER BY rev, " + idColumn);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private List<Integer> revisionsIn(String qualifiedTable) {
        return jdbc.queryForList("SELECT rev FROM " + qualifiedTable + " WHERE rev > ? ORDER BY rev", Integer.class,
                fixture.baseline());
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
    }

    private JsonNode listing() throws Exception {
        return json(get("/v1/audit").param("from", LISTING_RANGE_FROM).param("to", LISTING_RANGE_TO)
                .param("size", "200"));
    }

    private static JsonNode entryOfRevision(JsonNode page, int revision) {
        for (JsonNode entry : page.get("content")) {
            if (entry.get("revision").asInt() == revision) {
                return entry;
            }
        }
        return null;
    }

    // ---- what is archived and what is deleted ----------------------------------------------------------------

    @Test
    @DisplayName("archiva R1, las ocurrencias de R2 y R3 con los mismos valores y las borra de public; R4 a R8 no se mueven")
    void archivesAndDeletesTheRightRows() {
        List<Map<String, Object>> occurrences = rows("public.ocurrencia_aud", "id_ocurrencia", List.of(s.r1(), s.r2()));
        List<Map<String, Object>> allocations = rows("public.asignacion_aula_aud", "id_asignacion", List.of(s.r1(), s.r3()));
        List<Map<String, Object>> keptBefore = rows("public.ocurrencia_aud", "id_ocurrencia", s.kept());
        assertThat(occurrences).hasSize(5);
        assertThat(allocations).hasSize(3);

        AuditArchiveResultDto result = archiveAsSystem();

        assertThat(result).isEqualTo(new AuditArchiveResultDto(AuditArchiveOutcome.COMPLETED,
                AuditArchiveFixture.RETAINED_FROM_CYCLE, AuditArchiveFixture.CUTOFF, 5, 3, 2));
        assertThat(rows("archivo.ocurrencia_aud", "id_ocurrencia", List.of(s.r1(), s.r2()))).isEqualTo(occurrences);
        assertThat(rows("archivo.asignacion_aula_aud", "id_asignacion", List.of(s.r1(), s.r3()))).isEqualTo(allocations);
        assertThat(rows("public.ocurrencia_aud", "id_ocurrencia", List.of(s.r1(), s.r2()))).isEmpty();
        assertThat(rows("public.asignacion_aula_aud", "id_asignacion", List.of(s.r1(), s.r3()))).isEmpty();
        assertThat(rows("public.ocurrencia_aud", "id_ocurrencia", s.kept())).isEqualTo(keptBefore).hasSize(5);
        assertThat(rows("archivo.ocurrencia_aud", "id_ocurrencia", s.kept())).isEmpty();
        assertThat(count("SELECT count(*) FROM archivo.ocurrencia_aud")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM archivo.asignacion_aula_aud")).isEqualTo(3);
    }

    @Test
    @DisplayName("la solicitud de R2 no se archiva: sigue en public.solicitud_aula_aud")
    void requestRowOfTheMixedRevisionStays() {
        archiveAsSystem();

        assertThat(count("SELECT count(*) FROM solicitud_aula_aud WHERE rev = ?", s.r2())).isEqualTo(1);
    }

    // ---- revinfo ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("revinfo: R1 y R3 quedan solo en archivo, R2 (mixta) en las dos y todo rev archivado existe en archivo.revinfo")
    void revinfoPlacement() {
        Map<String, Object> r2Before = jdbc.queryForMap("SELECT * FROM public.revinfo WHERE rev = ?", s.r2());

        archiveAsSystem();

        assertThat(revisionsIn("public.revinfo")).doesNotContain(s.r1(), s.r3()).contains(s.r2());
        assertThat(revisionsIn("archivo.revinfo")).containsExactly(s.r1(), s.r2(), s.r3());
        assertThat(jdbc.queryForMap("SELECT * FROM archivo.revinfo WHERE rev = ?", s.r2())).isEqualTo(r2Before);
        assertThat(revisionsIn("public.revinfo")).contains(s.r4(), s.r5(), s.r6(), s.r7(), s.r8());
        assertThat(count("SELECT count(*) FROM (SELECT rev FROM archivo.ocurrencia_aud UNION SELECT rev FROM "
                + "archivo.asignacion_aula_aud) a WHERE NOT EXISTS (SELECT 1 FROM archivo.revinfo r WHERE r.rev = a.rev)"))
                .isZero();
    }

    // ---- revinfo_resumen -------------------------------------------------------------------------------------

    @Test
    @DisplayName("resumen: public descuenta lo archivado (R2 queda con solo la solicitud) y archivo guarda la cantidad original")
    void summaryIsDiscountedInPublicAndCompleteInArchive() {
        archiveAsSystem();

        List<Map<String, Object>> publicR2 = jdbc.queryForList(
                "SELECT tabla_aud, revtype, cantidad FROM public.revinfo_resumen WHERE rev = ?", s.r2());
        assertThat(publicR2).singleElement().satisfies(row -> {
            assertThat(row.get("tabla_aud")).isEqualTo("solicitud_aula_aud");
            assertThat(((Number) row.get("revtype")).intValue()).isZero();
            assertThat(((Number) row.get("cantidad")).intValue()).isEqualTo(1);
        });
        assertThat(count("SELECT count(*) FROM public.revinfo_resumen WHERE rev IN (?, ?)", s.r1(), s.r3())).isZero();
        assertThat(jdbc.queryForObject("SELECT cantidad FROM archivo.revinfo_resumen WHERE rev = ? "
                + "AND tabla_aud = 'ocurrencia_aud' AND revtype = 0", Integer.class, s.r2())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT cantidad FROM archivo.revinfo_resumen WHERE rev = ? "
                + "AND tabla_aud = 'ocurrencia_aud'", Integer.class, s.r1())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT cantidad FROM archivo.revinfo_resumen WHERE rev = ? "
                + "AND tabla_aud = 'asignacion_aula_aud'", Integer.class, s.r1())).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM archivo.revinfo_resumen WHERE tabla_aud = 'solicitud_aula_aud'")).isZero();
        assertThat(count("SELECT count(*) FROM public.revinfo_resumen WHERE rev IN (?, ?, ?, ?, ?)",
                s.r4(), s.r5(), s.r6(), s.r7(), s.r8())).isEqualTo(5);
    }

    // ---- listing and chain -----------------------------------------------------------------------------------

    @Test
    @DisplayName("el listado antes trae R2 con 3 registros; después solo la solicitud, sin R1 ni R3 y con R4 a R8 intactos")
    void listingStaysCoherent() throws Exception {
        JsonNode before = listing();
        JsonNode r2Before = entryOfRevision(before, s.r2());
        assertThat(r2Before.get("recordCount").asInt()).isEqualTo(3);
        assertThat(entryOfRevision(before, s.r1())).isNotNull();
        assertThat(entryOfRevision(before, s.r3())).isNotNull();

        archiveAsSystem();

        JsonNode after = listing();
        JsonNode r2After = entryOfRevision(after, s.r2());
        // One surviving row turns the TRANSACTION into a CHANGE of the request.
        assertThat(r2After.get("type").asText()).isEqualTo("CHANGE");
        assertThat(r2After.get("entityType").asText()).isEqualTo("Solicitud de aula");
        assertThat(r2After.get("kind").asText()).isEqualTo("CREATED");
        assertThat(entryOfRevision(after, s.r1())).isNull();
        assertThat(entryOfRevision(after, s.r3())).isNull();
        for (int kept : s.kept()) {
            assertThat(entryOfRevision(after, kept)).as("revision %d", kept).isEqualTo(entryOfRevision(before, kept));
        }
    }

    @Test
    @DisplayName("la cadena de la operación hija sigue devolviendo padre e hijo porque el padre no se archiva")
    void chainKeepsParentAndChild() throws Exception {
        archiveAsSystem();

        JsonNode chain = json(get("/v1/audit/operations/{id}/chain", s.childOperation()));

        assertThat(chain.get("truncated").asBoolean()).isFalse();
        List<String> operations = new ArrayList<>();
        chain.get("entries").forEach(entry -> operations.add(entry.get("operationId").asText()));
        assertThat(operations).containsExactly(s.parentOperation(), s.childOperation());
    }

    @Test
    @DisplayName("con un abuelo archivado la cadena desde el hijo arranca en el primer sobreviviente")
    void chainStartsAtTheFirstSurvivorWhenAGrandparentIsArchived() throws Exception {
        String grandparent = java.util.UUID.randomUUID().toString();
        int g = fixture.revision(LocalDateTime.of(2002, 6, 11, 10, 0), grandparent, null);
        fixture.occurrences(g, 9051, 1);
        jdbc.update("UPDATE revinfo SET operacion_padre_id = ? WHERE rev = ?", grandparent, s.r6());

        AuditArchiveResultDto result = archiveAsSystem();

        assertThat(result.outcome()).isEqualTo(AuditArchiveOutcome.COMPLETED);
        assertThat(revisionsIn("public.revinfo")).doesNotContain(g);
        assertThat(revisionsIn("archivo.revinfo")).contains(g);
        JsonNode chain = json(get("/v1/audit/operations/{id}/chain", s.childOperation()));
        List<String> operations = new ArrayList<>();
        chain.get("entries").forEach(entry -> operations.add(entry.get("operationId").asText()));
        assertThat(operations).containsExactly(s.parentOperation(), s.childOperation());
        assertThat(chain.get("truncated").asBoolean()).isFalse();
    }

    // ---- audit of the purge ----------------------------------------------------------------------------------

    @Test
    @DisplayName("la corrida deja un archivado_auditoria COMPLETED con los contadores y dos revisiones SYSTEM con la descripción final")
    void runIsRecordedAsASystemOperation() throws Exception {
        archiveAsSystem();

        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM archivado_auditoria");
        assertThat(run.get("estado")).isEqualTo("COMPLETED");
        assertThat(run.get("ciclo_conservado_desde")).isEqualTo(AuditArchiveFixture.RETAINED_FROM_CYCLE);
        assertThat(run.get("fecha_corte")).hasToString("2003-03-03");
        assertThat(((Number) run.get("filas_ocurrencia")).longValue()).isEqualTo(5);
        assertThat(((Number) run.get("filas_asignacion")).longValue()).isEqualTo(3);
        assertThat(((Number) run.get("revisiones_borradas")).longValue()).isEqualTo(2);

        List<Map<String, Object>> revisions = jdbc.queryForList(
                "SELECT r.descripcion, r.tipo_actor FROM revinfo r JOIN archivado_auditoria_aud a ON a.rev = r.rev "
                        + "WHERE a.id_archivado = ?", run.get("id_archivado"));
        assertThat(revisions).hasSize(2).allSatisfy(revision -> {
            assertThat(revision.get("descripcion")).isEqualTo("Archivado de auditoría anterior al ciclo 2003: 8 filas");
            assertThat(revision.get("tipo_actor")).isEqualTo("SYSTEM");
        });

        JsonNode page = json(get("/v1/audit").param("entityType", "Archivado de auditoría").param("size", "50"));
        assertThat(page.get("content")).hasSize(1);
        JsonNode entry = page.get("content").get(0);
        assertThat(entry.get("type").asText()).isEqualTo("OPERATION");
        assertThat(entry.get("actorType").asText()).isEqualTo("SYSTEM");
        assertThat(entry.get("description").asText()).isEqualTo("Archivado de auditoría anterior al ciclo 2003: 8 filas");
    }

    // ---- idempotence, missing periods, lock ------------------------------------------------------------------

    @Test
    @DisplayName("la segunda corrida devuelve NOTHING_TO_ARCHIVE, no cambia ningún conteo y no abre otro registro")
    void secondRunChangesNothing() {
        archiveAsSystem();
        Map<String, Long> counts = fixture.counts();
        long runs = count("SELECT count(*) FROM archivado_auditoria");
        long auditedRevisions = count("SELECT count(*) FROM archivado_auditoria_aud");

        AuditArchiveResultDto second = archiveAsSystem();

        assertThat(second).isEqualTo(new AuditArchiveResultDto(AuditArchiveOutcome.NOTHING_TO_ARCHIVE,
                AuditArchiveFixture.RETAINED_FROM_CYCLE, AuditArchiveFixture.CUTOFF, 0, 0, 0));
        assertThat(fixture.counts()).isEqualTo(counts);
        assertThat(count("SELECT count(*) FROM archivado_auditoria")).isEqualTo(runs).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM archivado_auditoria_aud")).isEqualTo(auditedRevisions);
    }

    @Test
    @DisplayName("sin períodos del año en curso devuelve NO_PERIODS y no cambia public, archivo ni el registro de corridas")
    void noPeriodsChangesNothing() {
        Map<String, Long> counts = fixture.counts();

        AuditArchiveResultDto result = archiveAsSystem(LocalDate.of(1990, 6, 1));

        assertThat(result).isEqualTo(new AuditArchiveResultDto(AuditArchiveOutcome.NO_PERIODS, null, null, 0, 0, 0));
        assertThat(fixture.counts()).isEqualTo(counts);
        assertThat(count("SELECT count(*) FROM archivado_auditoria")).isZero();
    }

    @Test
    @DisplayName("con el advisory lock tomado desde otra conexión devuelve STOPPED_BY_CONCURRENT_RUN, no mueve filas y no abre registro")
    void busyLockStopsTheRun() throws Exception {
        Map<String, Long> counts = fixture.counts();

        AuditArchiveResultDto result;
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try {
                other.createStatement().execute("SELECT pg_advisory_xact_lock(hashtext('siga.audit.archive'), 0)");
                result = archiveAsSystem();
            } finally {
                other.rollback();
            }
        }

        assertThat(result.outcome()).isEqualTo(AuditArchiveOutcome.STOPPED_BY_CONCURRENT_RUN);
        assertThat(result.occurrenceRows()).isZero();
        assertThat(result.allocationRows()).isZero();
        assertThat(fixture.counts()).isEqualTo(counts);
        assertThat(count("SELECT count(*) FROM public.ocurrencia_aud WHERE rev = ?", s.r1())).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM archivado_auditoria")).isZero();
    }

    @Test
    @DisplayName("al soltar el lock la corrida siguiente archiva normalmente")
    void releasedLockLetsTheNextRunWork() throws Exception {
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            other.createStatement().execute("SELECT pg_advisory_xact_lock(hashtext('siga.audit.archive'), 0)");
            archiveAsSystem();
            other.rollback();
        }

        assertThat(archiveAsSystem().outcome()).isEqualTo(AuditArchiveOutcome.COMPLETED);
    }

    // ---- verified copy ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("una fila ya presente en archivo con contenido distinto aborta el lote antes de borrar y deja el registro FAILED")
    void differingRowInTheArchiveAbortsBeforeDeleting() {
        jdbc.update("INSERT INTO archivo.ocurrencia_aud (id_ocurrencia, rev, revtype, id_evento_academico, fecha, estado) "
                + "VALUES (9001, ?, 0, 999, DATE '2001-05-10', 'ROOM_RELEASED')", s.r1());
        List<Map<String, Object>> publicBefore = rows("public.ocurrencia_aud", "id_ocurrencia", List.of(s.r1()));

        assertThatThrownBy(this::archiveAsSystem)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no coincide");

        assertThat(rows("public.ocurrencia_aud", "id_ocurrencia", List.of(s.r1()))).isEqualTo(publicBefore).hasSize(3);
        assertThat(count("SELECT count(*) FROM archivo.ocurrencia_aud")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM archivo.revinfo")).isZero();
        assertThat(count("SELECT count(*) FROM public.revinfo_resumen WHERE rev = ? AND tabla_aud = 'ocurrencia_aud'",
                s.r1())).isEqualTo(1);
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM archivado_auditoria");
        assertThat(run.get("estado")).isEqualTo("FAILED");
        assertThat(((Number) run.get("filas_ocurrencia")).longValue()).isZero();
        assertThat((String) run.get("mensaje_error")).contains("no coincide");
    }

    @Test
    @DisplayName("tras corregir la fila distinta, la corrida siguiente completa el archivado")
    void nextRunCompletesAfterTheConflictIsFixed() {
        jdbc.update("INSERT INTO archivo.ocurrencia_aud (id_ocurrencia, rev, revtype, id_evento_academico, fecha, estado) "
                + "VALUES (9001, ?, 0, 999, DATE '2001-05-10', 'ROOM_RELEASED')", s.r1());
        assertThatThrownBy(this::archiveAsSystem).hasRootCauseInstanceOf(IllegalStateException.class);
        jdbc.update("DELETE FROM archivo.ocurrencia_aud");

        AuditArchiveResultDto result = archiveAsSystem();

        assertThat(result.outcome()).isEqualTo(AuditArchiveOutcome.COMPLETED);
        assertThat(result.occurrenceRows()).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM archivo.ocurrencia_aud")).isEqualTo(5);
    }

    // ---- scheduler flag --------------------------------------------------------------------------------------

    @Test
    @DisplayName("con siga.audit.archive.enabled=false no existe el bean AuditArchiveScheduler")
    void schedulerIsAbsentWhenDisabled() {
        assertThat(applicationContext.getBeanNamesForType(AuditArchiveScheduler.class)).isEmpty();
    }
}
