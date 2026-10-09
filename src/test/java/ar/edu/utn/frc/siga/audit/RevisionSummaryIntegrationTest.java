package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.audit.internal.SigaRevisionListener;
import ar.edu.utn.frc.siga.audit.model.RevisionSummaryKey;
import ar.edu.utn.frc.siga.audit.model.SigaRevision;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks that real writes feed {@code revinfo_resumen} (listener plus {@code @ElementCollection} mapping) by
 * comparing it, for every revision created by a scenario, with a {@code GROUP BY rev, revtype} over the root
 * {@code _aud} tables. Then checks that {@code GET /v1/audit} reports the same counts under every
 * {@code entityType}, {@code kind} and {@code actor} filter. Commits are real (no {@code @Transactional}): Envers needs them.
 */
@Import(IntegrationTestData.class)
@DisplayName("Resumen por revisión contra las _aud (integración)")
class RevisionSummaryIntegrationTest extends AbstractIntegrationTest {

    private static final short ADD = 0;
    private static final short MOD = 1;
    private static final short DEL = 2;
    private static final Map<Integer, String> KIND_BY_REVTYPE = Map.of(0, "CREATED", 1, "MODIFIED", 2, "DELETED");

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private SettingsStore settingsStore;
    @Autowired
    private AuditedEntityRegistry registry;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private EntityManager entityManager;

    private final Map<SettingKey, String> originalSettings = new LinkedHashMap<>();

    private record Row(int rev, String table, int revtype, long count) {
    }

    /** Listing entry to look for: one operation (all its revisions) or one loose revision. */
    private record Target(String operationId, List<Integer> revisions) {
    }

    @AfterEach
    void restoreSettings() {
        if (originalSettings.isEmpty()) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> originalSettings.forEach(settingsStore::write));
        originalSettings.clear();
    }

    // ---- summary versus _aud ------------------------------------------------------------------------------

    private int baseline() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(rev), 0) FROM revinfo", Integer.class);
    }

    private List<Row> summaryRowsAfter(int baseline) {
        return jdbc.query("SELECT rev, tabla_aud, revtype, cantidad FROM revinfo_resumen WHERE rev > ? "
                        + "ORDER BY rev, tabla_aud, revtype", (rs, i) -> new Row(rs.getInt("rev"), rs.getString("tabla_aud"),
                        rs.getInt("revtype"), rs.getLong("cantidad")), baseline);
    }

    /** What the summary should hold: the grouped rows of the root {@code _aud} tables of the registry. */
    private List<Row> auditRowsAfter(int baseline) {
        List<Row> rows = new ArrayList<>();
        for (AuditedEntity entity : registry.all()) {
            rows.addAll(jdbc.query("SELECT rev, revtype, count(*) AS cantidad FROM " + entity.auditTable()
                            + " WHERE rev > ? GROUP BY rev, revtype",
                    (rs, i) -> new Row(rs.getInt("rev"), entity.auditTable(), rs.getInt("revtype"), rs.getLong("cantidad")),
                    baseline));
        }
        return rows.stream().sorted(java.util.Comparator.comparingInt(Row::rev)
                .thenComparing(Row::table).thenComparingInt(Row::revtype)).toList();
    }

    private void assertSummaryEqualsAuditSince(int baseline) {
        List<Row> expected = auditRowsAfter(baseline);
        assertThat(expected).as("the scenario must have written audited rows").isNotEmpty();
        assertThat(summaryRowsAfter(baseline)).containsExactlyElementsOf(expected);
    }

    private long auditRows(String table, int rev) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE rev = ?", Long.class, rev);
    }

    // ---- scenarios ----------------------------------------------------------------------------------------

    private Long createRecurringEvent(int daysAhead, int weeks) {
        var sc = testData.materiaYComision();
        LocalDate start = LocalDate.now().plusDays(daysAhead);
        var dto = new CreateRecurringEventRequestDto(30, LocalTime.of(8, 0), 90, start.getDayOfWeek(), start,
                start.plusWeeks(weeks - 1L), sc.subjectId(), sc.commissionId());
        return asFixtureUser(() -> academicEventService.createRecurringEvent(dto)).id();
    }

    private int revisionOfCreatedRecurringEvent(int daysAhead, int weeks) {
        createRecurringEvent(daysAhead, weeks);
        return jdbc.queryForObject("SELECT MAX(rev) FROM evento_academico_aud WHERE revtype = 0", Integer.class);
    }

    /** One transaction, no operation: 2 occurrences added, 1 modified, 2 deleted, plus 1 setting modified. */
    private int mixedLooseTransaction(int daysAhead) {
        Long eventId = createRecurringEvent(daysAhead, 4);
        LocalDate start = LocalDate.now().plusDays(daysAhead);
        originalSettings.putIfAbsent(SettingKey.PREVIEW_TTL_MINUTES, settingsStore.getRaw(SettingKey.PREVIEW_TTL_MINUTES));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
            AcademicEvent event = occurrences.getFirst().getEvent();
            occurrences.get(0).setStatus(OccurrenceStatus.ROOM_RELEASED);
            occurrenceRepository.delete(occurrences.get(1));
            occurrenceRepository.delete(occurrences.get(2));
            for (int offset = 1; offset <= 2; offset++) {
                occurrenceRepository.save(Occurrence.builder().event(event).date(start.plusDays(offset))
                        .status(OccurrenceStatus.NEEDS_ROOM).build());
            }
            settingsStore.write(SettingKey.PREVIEW_TTL_MINUTES,
                    String.valueOf(Long.parseLong(settingsStore.getRaw(SettingKey.PREVIEW_TTL_MINUTES)) + 1));
        });
        return jdbc.queryForObject("SELECT MAX(rev) FROM ocurrencia_aud WHERE revtype = 2", Integer.class);
    }

    private int publicRoomRequest() throws Exception {
        var sc = testData.materiaYComision();
        var building = testData.edificio();
        List<Long> classrooms = List.of(testData.aula(building).getId(), testData.aula(building).getId());
        Map<String, Object> requester = new LinkedHashMap<>();
        requester.put("scope", "GRADO");
        requester.put("teacherName", "Ada Lovelace");
        requester.put("teacherEmail", "ada@frc.utn.edu.ar");
        requester.put("teacherPhone", "351-1234567");
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("commissionId", sc.commissionId());
            item.put("date", LocalDate.now().plusDays(7 + i).toString());
            item.put("startTime", "10:00:00");
            item.put("endTime", "12:00:00");
            item.put("estimated", 35);
            item.put("classroomCount", 1);
            item.put("requiresProjector", true);
            item.put("requiresComputers", false);
            item.put("preferredClassroomIds", i == 0 ? classrooms : List.of());
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "PARTIAL_EXAM_OFF_SCHEDULE");
        body.put("requester", requester);
        body.put("subjectId", sc.subjectId());
        body.put("items", items);
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        anonymous.perform(post("/v1/room-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT MAX(rev) FROM solicitud_aula_aud WHERE revtype = 0", Integer.class);
    }

    private int batchSettingsPut() throws Exception {
        SettingKey first = SettingKey.PREVIEW_TTL_MINUTES;
        SettingKey second = SettingKey.PREVIEW_SUGGESTION_TIME_LIMIT_SECONDS;
        originalSettings.putIfAbsent(first, settingsStore.getRaw(first));
        originalSettings.putIfAbsent(second, settingsStore.getRaw(second));
        Map<String, Object> body = Map.of("settings", List.of(
                Map.of("key", first.getKey(), "value", String.valueOf(Long.parseLong(settingsStore.getRaw(first)) + 1)),
                Map.of("key", second.getKey(), "value", String.valueOf(Long.parseLong(settingsStore.getRaw(second)) + 1))));
        mockMvc.perform(put("/v1/settings").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        return jdbc.queryForObject("SELECT MAX(rev) FROM configuracion_aud", Integer.class);
    }

    /** Allocated occurrence released through the API; waits for the async listener to delete the allocation. */
    private long allocateAndRelease(int daysAhead) throws Exception {
        Long eventId = createRecurringEvent(daysAhead, 1);
        Long occurrenceId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
        Long classroomId = testData.aula(testData.edificio()).getId();
        var body = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)), null);
        MvcResult created = mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        long allocationId = objectMapper.readTree(created.getResponse().getContentAsString()).get(0).get("id").asLong();
        mockMvc.perform(post("/v1/events/occurrences/{id}/release", occurrenceId)).andExpect(status().is2xxSuccessful());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM asignacion_aula_aud WHERE id_asignacion = ? AND revtype = 2", Long.class, allocationId))
                .isEqualTo(1));
        return occurrenceId;
    }

    @Test
    @DisplayName("el alta de un evento recurrente con ocurrencias deja el resumen igual a las _aud y la subclase JOINED no duplica")
    void recurringEventCreation_summaryMatchesAudit() {
        int baseline = baseline();

        int rev = revisionOfCreatedRecurringEvent(90, 3);

        assertThat(auditRows("evento_recurrente_aud", rev)).as("the subclass row exists").isEqualTo(1);
        assertSummaryEqualsAuditSince(baseline);
        assertThat(summaryRowsAfter(baseline)).contains(
                new Row(rev, "evento_academico_aud", ADD, 1), new Row(rev, "ocurrencia_aud", ADD, 3));
        assertThat(summaryRowsAfter(baseline)).extracting(Row::table)
                .doesNotContain("evento_recurrente_aud", "evento_unico_aud");
    }

    @Test
    @DisplayName("PUT /v1/settings con dos claves deja una fila de resumen con cantidad 2")
    void settingsBatch_summaryMatchesAudit() throws Exception {
        int baseline = baseline();

        int rev = batchSettingsPut();

        assertSummaryEqualsAuditSince(baseline);
        assertThat(summaryRowsAfter(baseline)).containsExactly(new Row(rev, "configuracion_aud", MOD, 2));
    }

    @Test
    @DisplayName("la solicitud de aula con ítems y preferencias deja el resumen igual a las _aud")
    void roomRequestWithItemsAndPreferences_summaryMatchesAudit() throws Exception {
        int baseline = baseline();

        int rev = publicRoomRequest();

        assertSummaryEqualsAuditSince(baseline);
        assertThat(summaryRowsAfter(baseline)).contains(
                new Row(rev, "solicitud_aula_aud", ADD, 1),
                new Row(rev, "solicitud_aula_item_aud", ADD, 2),
                new Row(rev, "solicitud_aula_preferencia_aud", ADD, 2));
    }

    @Test
    @DisplayName("liberar una ocurrencia asignada deja el resumen igual a las _aud, incluida la baja de la asignación en otro hilo")
    void occurrenceRelease_summaryMatchesAudit() throws Exception {
        int baseline = baseline();

        long occurrenceId = allocateAndRelease(100);

        assertSummaryEqualsAuditSince(baseline);
        int releaseRev = jdbc.queryForObject(
                "SELECT MAX(rev) FROM ocurrencia_aud WHERE id_ocurrencia = ? AND revtype = 1", Integer.class, occurrenceId);
        assertThat(summaryRowsAfter(baseline)).contains(new Row(releaseRev, "ocurrencia_aud", MOD, 1));
        assertThat(summaryRowsAfter(baseline)).anyMatch(row -> row.table().equals("asignacion_aula_aud") && row.revtype() == DEL);
    }

    @Test
    @DisplayName("una transacción que mezcla altas, modificaciones y bajas deja una fila de resumen por tabla y tipo")
    void mixedAddModDelTransaction_summaryMatchesAudit() {
        int baseline = baseline();

        int rev = mixedLooseTransaction(110);

        assertSummaryEqualsAuditSince(baseline);
        assertThat(summaryRowsAfter(baseline)).contains(
                new Row(rev, "ocurrencia_aud", ADD, 2),
                new Row(rev, "ocurrencia_aud", MOD, 1),
                new Row(rev, "ocurrencia_aud", DEL, 2),
                new Row(rev, "configuracion_aud", MOD, 1));
    }

    // ---- listing versus _aud ------------------------------------------------------------------------------

    private JsonNode listing(String... params) throws Exception {
        var request = get("/v1/audit").param("size", "200");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Target targetOf(int rev) {
        String operationId = jdbc.queryForObject("SELECT operacion_id FROM revinfo WHERE rev = ?", String.class, rev);
        if (operationId == null) {
            return new Target(null, List.of(rev));
        }
        return new Target(operationId, jdbc.queryForList(
                "SELECT rev FROM revinfo WHERE operacion_id = ? ORDER BY rev", Integer.class, operationId));
    }

    private JsonNode entryOf(JsonNode page, Target target) {
        for (JsonNode entry : page.get("content")) {
            boolean sameGroup = target.operationId() != null
                    ? target.operationId().equals(entry.path("operationId").asText(null))
                    : entry.path("operationId").isMissingNode() || entry.path("operationId").isNull()
                            ? target.revisions().contains(entry.get("revision").asInt()) : false;
            if (sameGroup) {
                return entry;
            }
        }
        return null;
    }

    private String revisionList(Collection<Integer> revisions) {
        return revisions.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Rows in the {@code _aud} tables of the revisions, restricted by label and revtype when given. */
    private Map<String, Long> auditRowsByLabel(Target target, String label, Integer revtype) {
        Map<String, Long> byLabel = new LinkedHashMap<>();
        for (AuditedEntity entity : registry.all()) {
            if (label != null && !entity.label().equals(label)) {
                continue;
            }
            long count = jdbc.queryForObject("SELECT count(*) FROM " + entity.auditTable() + " WHERE rev IN ("
                    + revisionList(target.revisions()) + ")" + (revtype != null ? " AND revtype = " + revtype : ""), Long.class);
            if (count > 0) {
                byLabel.put(entity.label(), count);
            }
        }
        return byLabel;
    }

    /** Every label x kind combination of the listing must report what the {@code _aud} tables hold. */
    private void assertListingMatchesAuditUnderEveryFilter(Target target) throws Exception {
        List<String> labels = new ArrayList<>();
        labels.add(null);
        registry.all().forEach(entity -> labels.add(entity.label()));
        List<Integer> revtypes = new ArrayList<>();
        revtypes.add(null);
        revtypes.addAll(List.of(0, 1, 2));
        int groupsWithEntry = 0;
        for (String label : labels) {
            for (Integer revtype : revtypes) {
                List<String> params = new ArrayList<>();
                if (label != null) {
                    params.addAll(List.of("entityType", label));
                }
                if (revtype != null) {
                    params.addAll(List.of("kind", KIND_BY_REVTYPE.get(revtype)));
                }
                JsonNode entry = entryOf(listing(params.toArray(String[]::new)), target);
                Map<String, Long> expected = auditRowsByLabel(target, label, revtype);
                String context = "label=" + label + " revtype=" + revtype;
                if (expected.isEmpty()) {
                    assertThat(entry).as("no rows, entry must be absent: " + context).isNull();
                    continue;
                }
                groupsWithEntry++;
                assertThat(entry).as("entry present: " + context).isNotNull();
                long expectedCount = expected.values().stream().mapToLong(Long::longValue).sum();
                if (expectedCount == 1 && target.operationId() == null) {
                    // A loose group left with a single row is reported as a CHANGE: no recordCount, but the record id.
                    String entityLabel = expected.keySet().iterator().next();
                    assertThat(entry.get("type").asText()).as("type: " + context).isEqualTo("CHANGE");
                    assertThat(entry.get("entityType").asText()).as("entityType: " + context).isEqualTo(entityLabel);
                    assertThat(entry.get("recordId").asText()).as("recordId: " + context)
                            .isEqualTo(singleRecordId(target, entityLabel, revtype));
                    continue;
                }
                assertThat(entry.get("recordCount").asLong()).as("recordCount: " + context).isEqualTo(expectedCount);
                assertThat(objectMapper.convertValue(entry.get("entityTypes"), List.class)).as("entityTypes: " + context)
                        .containsExactlyElementsOf(expected.keySet().stream().sorted().toList());
            }
        }
        assertThat(groupsWithEntry).as("the unfiltered listing found the group").isPositive();
    }

    private String singleRecordId(Target target, String label, Integer revtype) {
        AuditedEntity entity = registry.byLabel(label).orElseThrow();
        return jdbc.queryForObject("SELECT CAST(" + entity.idColumn() + " AS varchar) FROM " + entity.auditTable()
                + " WHERE rev IN (" + revisionList(target.revisions()) + ")" + (revtype != null ? " AND revtype = " + revtype : ""),
                String.class);
    }

    /** The group shows up under its own actor type and not under the other. */
    private void assertActorFilter(Target target) throws Exception {
        String actor = jdbc.queryForObject("SELECT tipo_actor FROM revinfo WHERE rev = ?", String.class,
                target.revisions().getLast());
        String other = actor.equals("HUMAN") ? "SYSTEM" : "HUMAN";
        JsonNode own = entryOf(listing("actor", actor), target);
        assertThat(own).as("listed under " + actor).isNotNull();
        assertThat(own.get("recordCount")).isEqualTo(entryOf(listing(), target).get("recordCount"));
        assertThat(entryOf(listing("actor", other), target)).as("not listed under " + other).isNull();
    }

    @Test
    @DisplayName("GET /v1/audit: alta de evento recurrente (operación HUMAN) coincide con las _aud bajo todos los filtros entityType y kind")
    void listing_recurringEventOperation_matchesAudit() throws Exception {
        Target target = targetOf(revisionOfCreatedRecurringEvent(120, 3));

        assertThat(target.operationId()).isNotNull();
        assertListingMatchesAuditUnderEveryFilter(target);
        assertActorFilter(target);
    }

    @Test
    @DisplayName("GET /v1/audit: transacción suelta con altas, modificaciones y bajas (SYSTEM) coincide con las _aud bajo todos los filtros")
    void listing_mixedLooseTransaction_matchesAudit() throws Exception {
        Target target = targetOf(mixedLooseTransaction(130));

        assertThat(target.operationId()).isNull();
        assertListingMatchesAuditUnderEveryFilter(target);
        assertActorFilter(target);
    }

    @Test
    @DisplayName("GET /v1/audit: solicitud de aula pública con ítems y preferencias coincide con las _aud bajo todos los filtros")
    void listing_publicRoomRequest_matchesAudit() throws Exception {
        Target target = targetOf(publicRoomRequest());

        assertListingMatchesAuditUnderEveryFilter(target);
        assertActorFilter(target);
    }

    @Test
    @DisplayName("GET /v1/audit: PUT /v1/settings con dos claves (operación) coincide con las _aud")
    void listing_settingsBatch_matchesAudit() throws Exception {
        Target target = targetOf(batchSettingsPut());

        assertThat(target.operationId()).isNotNull();
        assertListingMatchesAuditUnderEveryFilter(target);
        assertActorFilter(target);
    }

    @Test
    @DisplayName("GET /v1/audit: un cambio suelto de una sola fila trae el recordId leído de la _aud")
    void listing_singleLooseChange_carriesRecordId() throws Exception {
        originalSettings.putIfAbsent(SettingKey.PREVIEW_TTL_MINUTES, settingsStore.getRaw(SettingKey.PREVIEW_TTL_MINUTES));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> settingsStore.write(SettingKey.PREVIEW_TTL_MINUTES,
                String.valueOf(Long.parseLong(settingsStore.getRaw(SettingKey.PREVIEW_TTL_MINUTES)) + 1)));
        int rev = jdbc.queryForObject("SELECT MAX(rev) FROM configuracion_aud", Integer.class);

        JsonNode entry = entryOf(listing(), targetOf(rev));

        assertThat(entry).isNotNull();
        assertThat(entry.get("type").asText()).isEqualTo("CHANGE");
        assertThat(entry.get("entityType").asText()).isEqualTo("Configuración");
        assertThat(entry.get("recordId").asText()).isEqualTo(SettingKey.PREVIEW_TTL_MINUTES.getKey());
    }

    @Test
    @DisplayName("GET /v1/audit: un cambio suelto de una ocurrencia trae su id como recordId, leído de ocurrencia_aud")
    void listing_singleLooseOccurrenceChange_carriesOccurrenceId() throws Exception {
        Long eventId = createRecurringEvent(140, 1);
        Long occurrenceId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                occurrenceRepository.findById(occurrenceId).orElseThrow().setStatus(OccurrenceStatus.ROOM_RELEASED));
        int rev = jdbc.queryForObject("SELECT MAX(rev) FROM ocurrencia_aud WHERE revtype = 1", Integer.class);

        JsonNode entry = entryOf(listing(), targetOf(rev));

        assertThat(entry).isNotNull();
        assertThat(entry.get("type").asText()).isEqualTo("CHANGE");
        assertThat(entry.get("entityType").asText()).isEqualTo("Ocurrencia");
        assertThat(entry.get("recordId").asText()).isEqualTo(String.valueOf(occurrenceId));
    }

    // ---- drift ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("cada entidad raíz del registry resuelve en el listener a su propia auditTable")
    void listenerRootTable_equalsRegistryAuditTable() {
        SigaRevisionListener listener = new SigaRevisionListener();
        assertThat(registry.all()).isNotEmpty();

        for (AuditedEntity entity : registry.all()) {
            assertThat(summaryTableOf(listener, entity.javaType()))
                    .as("root table of " + entity.jpaName()).isEqualTo(entity.auditTable());
        }
    }

    @Test
    @DisplayName("cada entidad @Audited, subclases JOINED incluidas, resuelve a la auditTable de una raíz del registry")
    void listenerRootTable_ofEveryAuditedEntity_isARegistryTable() {
        SigaRevisionListener listener = new SigaRevisionListener();
        Set<String> registryTables = registry.all().stream().map(AuditedEntity::auditTable).collect(Collectors.toSet());
        List<Class<?>> auditedTypes = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType).filter(type -> type.isAnnotationPresent(Audited.class))
                .<Class<?>>map(type -> type).toList();
        assertThat(auditedTypes.size()).isGreaterThan(registry.all().size());

        for (Class<?> type : auditedTypes) {
            assertThat(registryTables).as("table of " + type.getSimpleName()).contains(summaryTableOf(listener, type));
        }
    }

    @Test
    @DisplayName("el SQL de backfill de la migración nombra exactamente las auditTable del registry")
    void backfillSql_namesExactlyTheRegistryAuditTables() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(RevisionSummaryMigration.RESOURCE_PATTERN);
        assertThat(resources).as("migration V" + RevisionSummaryMigration.VERSION).hasSize(1);
        String sql = resources[0].getContentAsString(StandardCharsets.UTF_8);

        Set<String> literals = new TreeSet<>();
        Matcher matcher = Pattern.compile("'([a-z_]+_aud)'").matcher(sql);
        while (matcher.find()) {
            literals.add(matcher.group(1));
        }
        Set<String> registryTables = registry.all().stream().map(AuditedEntity::auditTable)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(literals).containsExactlyElementsOf(registryTables);
        for (String table : registryTables) {
            assertThat(sql).as("reads " + table).containsPattern("FROM " + table + " GROUP BY rev, revtype");
        }
    }

    private static String summaryTableOf(SigaRevisionListener listener, Class<?> type) {
        SigaRevision revision = new SigaRevision();
        listener.entityChanged(type, type.getName(), 1L, RevisionType.ADD, revision);
        assertThat(revision.getSummary()).hasSize(1);
        RevisionSummaryKey key = revision.getSummary().keySet().iterator().next();
        return key.auditTable();
    }
}
