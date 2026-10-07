package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración del registro unificado de auditoría contra Postgres real: genera revisiones
 * de Envers en varias entidades (evento académico + configuración) con commits reales
 * (sin {@code @Transactional}, Envers lo exige) y consulta {@code GET /v1/audit}.
 */
@Import(IntegrationTestData.class)
@DisplayName("Audit Registry API (integración)")
class AuditRegistryApiIntegrationTest extends AbstractIntegrationTest {

    private static final String USER = "integration-test@frc.utn.edu.ar";

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private SettingsStore settingsStore;
    @Autowired
    private AuditedEntityRegistry registry;
    @Autowired
    private RecurringEventRepository recurringEventRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;

    /** Original values of the keys the test touched via {@link #writeSettingsInOneTransaction}. */
    private final Map<SettingKey, String> originalSettings = new LinkedHashMap<>();

    @AfterEach
    void restoreSettings() {
        if (originalSettings.isEmpty()) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                originalSettings.forEach(settingsStore::write));
        originalSettings.clear();
    }

    /**
     * Writes the keys in a single transaction, bypassing {@code SettingsServiceImpl}, so
     * there is no {@code @AuditOperation}: Envers stamps a revision without {@code operacion_id}. Returns that revision.
     */
    private int writeSettingsInOneTransaction(SettingKey... keys) {
        for (SettingKey key : keys) {
            originalSettings.putIfAbsent(key, settingsStore.getRaw(key));
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            for (SettingKey key : keys) {
                // numeric +1: guarantees a different value and therefore a MOD row in the _aud table
                settingsStore.write(key, String.valueOf(Long.parseLong(settingsStore.getRaw(key)) + 1));
            }
        });
        return jdbcTemplate.queryForObject("SELECT MAX(rev) FROM configuracion_aud", Integer.class);
    }

    /** Recurring event with {@code occurrences} occurrences persisted in a single transaction, without an operation. */
    private int seedLargeLooseTransaction(int occurrences) {
        var sc = testData.materiaYComision();
        LocalDate start = LocalDate.now().plusDays(400);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            RecurringEvent event = recurringEventRepository.save(RecurringEvent.builder()
                    .enrolled(30).startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(90))
                    .dayOfWeek(start.getDayOfWeek()).startDate(start).endDate(start.plusDays(occurrences))
                    .subjectId(sc.subjectId()).commissionId(sc.commissionId()).build());
            List<Occurrence> rows = new ArrayList<>();
            for (int i = 0; i < occurrences; i++) {
                rows.add(Occurrence.builder().event(event).date(start.plusDays(i))
                        .status(OccurrenceStatus.NEEDS_ROOM).build());
            }
            occurrenceRepository.saveAll(rows);
        });
        return jdbcTemplate.queryForObject("SELECT MAX(rev) FROM evento_academico_aud", Integer.class);
    }

    private long auditRowsOfRevision(int revision) {
        long total = 0;
        for (AuditedEntity entity : registry.all()) {
            total += jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + entity.auditTable() + " WHERE rev = ?", Long.class, revision);
        }
        return total;
    }

    private String latestOperationId(String description) {
        return jdbcTemplate.queryForObject(
                "SELECT operacion_id FROM revinfo WHERE descripcion = ? AND operacion_id IS NOT NULL "
                        + "ORDER BY rev DESC LIMIT 1", String.class, description);
    }

    private long operationRows(String table, String operationId, Integer revtype) {
        String sql = "SELECT COUNT(*) FROM " + table + " x JOIN revinfo r ON r.rev = x.rev WHERE r.operacion_id = ?"
                + (revtype != null ? " AND x.revtype = " + revtype : "");
        return jdbcTemplate.queryForObject(sql, Long.class, operationId);
    }

    private JsonNode json(MockMvc client, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        MvcResult result = client.perform(req).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** List entries that belong to a revision (a group without an operation appears only once). */
    private List<JsonNode> entriesOfRevision(JsonNode page, int revision) {
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode entry : page.get("content")) {
            if (entry.get("revision").asInt() == revision) {
                found.add(entry);
            }
        }
        return found;
    }

    private JsonNode operationEntry(JsonNode page, String operationId) {
        for (JsonNode entry : page.get("content")) {
            if (operationId.equals(entry.path("operationId").asText(null))) {
                return entry;
            }
        }
        throw new AssertionError("No hay una entrada OPERATION con operationId " + operationId);
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }

    private void seedRecurringEvent(LocalDate date) {
        var sc = testData.materiaYComision();
        var dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        asFixtureUser(() -> academicEventService.createRecurringEvent(dto));
    }

    private void bumpSetting(String value) throws Exception {
        mockMvc.perform(put("/v1/settings/{key}", "events.hours.end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/audit une revisiones de varias entidades con su etiqueta de dominio")
    void returnsUnifiedLogAcrossEntities() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(30));
        bumpSetting("21:45");

        mockMvc.perform(get("/v1/audit").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                // createRecurringEvent and the settings PUT carry @AuditOperation: they are OPERATION
                // and their entities appear in entityTypes, not in entityType.
                .andExpect(jsonPath("$.content[*].type", hasItem("OPERATION")))
                .andExpect(jsonPath("$.content[*].entityTypes[*]", hasItem("Evento académico")))
                .andExpect(jsonPath("$.content[*].entityTypes[*]", hasItem("Configuración")))
                .andExpect(jsonPath("$.content[*].entityTypes[*]", hasItem("Ocurrencia")))
                .andExpect(jsonPath("$.content[*].description", everyItem(not(blankOrNullString()))));
    }

    @Test
    @DisplayName("un cambio suelto sin @AuditOperation trae una descripción derivada del tipo de cambio")
    void looseChangeHasTemplatedDescription() throws Exception {
        int revision = writeSettingsInOneTransaction(SettingKey.PREVIEW_TTL_MINUTES);

        JsonNode entries = json(mockMvc, get("/v1/audit").param("size", "200").param("entityType", "Configuración"));

        List<JsonNode> found = entriesOfRevision(entries, revision);
        assertThat(found).hasSize(1);
        JsonNode change = found.getFirst();
        assertThat(change.get("type").asText()).isEqualTo("CHANGE");
        assertThat(change.get("entityType").asText()).isEqualTo("Configuración");
        assertThat(change.get("kind").asText()).isEqualTo("MODIFIED");
        assertThat(change.get("description").asText()).isEqualTo("Modificación de Configuración");
        assertThat(isAbsent(change.get("operationId"))).isTrue();
    }

    @Test
    @DisplayName("una transacción sin operación con varias filas sale como una sola entrada TRANSACTION")
    void multiRowLooseRevisionIsOneTransactionEntry() throws Exception {
        int revision = writeSettingsInOneTransaction(
                SettingKey.PREVIEW_TTL_MINUTES, SettingKey.PREVIEW_SUGGESTION_TIME_LIMIT_SECONDS);

        JsonNode entries = json(mockMvc, get("/v1/audit").param("size", "200"));

        List<JsonNode> found = entriesOfRevision(entries, revision);
        assertThat(found).hasSize(1);
        JsonNode transaction = found.getFirst();
        assertThat(transaction.get("type").asText()).isEqualTo("TRANSACTION");
        assertThat(transaction.get("recordCount").asInt()).isEqualTo(2);
        assertThat(isAbsent(transaction.get("operationId"))).isTrue();
        assertThat(isAbsent(transaction.get("entityType"))).isTrue();
        assertThat(isAbsent(transaction.get("recordId"))).isTrue();
        assertThat(transaction.get("kind").asText()).isEqualTo("MODIFIED");
        assertThat(transaction.get("description").asText()).isEqualTo("2 cambios en Configuración");
        assertThat(objectMapper.convertValue(transaction.get("entityTypes"), List.class))
                .containsExactly("Configuración");
    }

    @Test
    @DisplayName("GET /v1/audit/revisions/{rev} lista los cambios de la transacción y pagina en la base")
    void revisionDrillDownListsAndPaginates() throws Exception {
        int revision = writeSettingsInOneTransaction(
                SettingKey.PREVIEW_TTL_MINUTES, SettingKey.PREVIEW_SUGGESTION_TIME_LIMIT_SECONDS);

        mockMvc.perform(get("/v1/audit/revisions/{revision}", revision))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].type", everyItem(is("CHANGE"))))
                .andExpect(jsonPath("$.content[*].revision", everyItem(is(revision))))
                .andExpect(jsonPath("$.content[*].recordId", contains(
                        SettingKey.PREVIEW_SUGGESTION_TIME_LIMIT_SECONDS.getKey(),
                        SettingKey.PREVIEW_TTL_MINUTES.getKey())));

        JsonNode first = json(mockMvc, get("/v1/audit/revisions/{revision}", revision).param("size", "1")
                .param("page", "0"));
        JsonNode second = json(mockMvc, get("/v1/audit/revisions/{revision}", revision).param("size", "1")
                .param("page", "1"));
        assertThat(first.get("content")).hasSize(1);
        assertThat(second.get("content")).hasSize(1);
        assertThat(first.get("page").get("totalElements").asInt()).isEqualTo(2);
        assertThat(first.get("content").get(0).get("recordId").asText())
                .isNotEqualTo(second.get("content").get(0).get("recordId").asText());
    }

    @Test
    @DisplayName("una transacción grande es una sola entrada y su drill-down pagina con el total real")
    void largeTransactionPaginatesInDatabase() throws Exception {
        int revision = seedLargeLooseTransaction(45);
        long expected = auditRowsOfRevision(revision);
        assertThat(expected).isGreaterThanOrEqualTo(46);

        JsonNode listing = json(mockMvc, get("/v1/audit").param("size", "200"));
        List<JsonNode> entries = entriesOfRevision(listing, revision);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().get("type").asText()).isEqualTo("TRANSACTION");
        assertThat(entries.getFirst().get("recordCount").asLong()).isEqualTo(expected);

        JsonNode firstPage = json(mockMvc, get("/v1/audit/revisions/{revision}", revision).param("size", "20"));
        assertThat(firstPage.get("page").get("totalElements").asLong()).isEqualTo(expected);
        assertThat(firstPage.get("content")).hasSize(20);
        JsonNode lastPage = json(mockMvc, get("/v1/audit/revisions/{revision}", revision)
                .param("size", "20").param("page", String.valueOf((expected - 1) / 20)));
        assertThat(lastPage.get("content")).hasSize((int) (expected - ((expected - 1) / 20) * 20));
    }

    @Test
    @DisplayName("el filtro entityType reclasifica una transacción: con un solo registro que pasa el filtro es CHANGE")
    void entityTypeFilterTurnsTransactionIntoChange() throws Exception {
        int revision = seedLargeLooseTransaction(5);

        JsonNode filtered = json(mockMvc, get("/v1/audit").param("size", "200").param("entityType", "Evento académico"));

        List<JsonNode> entries = entriesOfRevision(filtered, revision);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().get("type").asText()).isEqualTo("CHANGE");
        assertThat(entries.getFirst().get("entityType").asText()).isEqualTo("Evento académico");

        JsonNode occurrences = json(mockMvc, get("/v1/audit").param("size", "200").param("entityType", "Ocurrencia"));
        JsonNode occurrenceEntry = entriesOfRevision(occurrences, revision).getFirst();
        assertThat(occurrenceEntry.get("type").asText()).isEqualTo("TRANSACTION");
        assertThat(occurrenceEntry.get("recordCount").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("PUT /v1/settings/{key} queda como OPERATION con su descripción y drill-down al cambio")
    void settingsPutIsAnOperation() throws Exception {
        bumpSetting("21:20");
        String operationId = latestOperationId("Modificación de configuración");

        JsonNode listing = json(mockMvc, get("/v1/audit").param("size", "200").param("entityType", "Configuración"));
        JsonNode operation = operationEntry(listing, operationId);
        assertThat(operation.get("type").asText()).isEqualTo("OPERATION");
        assertThat(operation.get("description").asText()).isEqualTo("Modificación de configuración");

        mockMvc.perform(get("/v1/audit/operations/{operationId}", operationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("CHANGE"))
                .andExpect(jsonPath("$.content[0].entityType").value("Configuración"))
                .andExpect(jsonPath("$.content[0].recordId").value("events.hours.end"));
    }

    @Test
    @DisplayName("drill-down de una operación inexistente responde 200 con página vacía")
    void operationDrillDown_unknownId_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/v1/audit/operations/{operationId}", "no-existe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("filtra por tipo de cambio: todas las entradas, OPERATION incluidas, traen ese kind")
    void filtersByKind() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(31));

        mockMvc.perform(get("/v1/audit").param("size", "200").param("kind", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[*].type", hasItem("OPERATION")))
                .andExpect(jsonPath("$.content[*].kind", everyItem(is("CREATED"))));
    }

    @Test
    @DisplayName("kind filtra las filas antes de agrupar: la OPERATION aparece con CREATED y no con DELETED")
    void kindFiltersRowsInsideOperations() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(38));
        String operationId = latestOperationId("Alta de evento recurrente");
        long created = operationRows("evento_academico_aud", operationId, 0)
                + operationRows("ocurrencia_aud", operationId, 0);
        long deleted = operationRows("evento_academico_aud", operationId, 2)
                + operationRows("ocurrencia_aud", operationId, 2);
        assertThat(created).isPositive();
        assertThat(deleted).isZero();

        JsonNode createdListing = json(mockMvc, get("/v1/audit").param("size", "200").param("kind", "CREATED"));
        JsonNode operation = operationEntry(createdListing, operationId);
        assertThat(operation.get("kind").asText()).isEqualTo("CREATED");

        JsonNode deletedListing = json(mockMvc, get("/v1/audit").param("size", "200").param("kind", "DELETED"));
        for (JsonNode entry : deletedListing.get("content")) {
            assertThat(entry.path("operationId").asText(null)).isNotEqualTo(operationId);
        }
        mockMvc.perform(get("/v1/audit/operations/{operationId}", operationId).param("kind", "DELETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("con los mismos filtros, recordCount del listado es igual a totalElements del drill-down")
    void groupRecordCountMatchesDrillDownTotalUnderFilters() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(39));
        String operationId = latestOperationId("Alta de evento recurrente");
        int revision = seedLargeLooseTransaction(7);

        for (String entityType : List.of("Ocurrencia", "Evento académico")) {
            JsonNode listing = json(mockMvc, get("/v1/audit").param("size", "200")
                    .param("entityType", entityType).param("kind", "CREATED"));
            JsonNode operation = operationEntry(listing, operationId);
            JsonNode drill = json(mockMvc, get("/v1/audit/operations/{operationId}", operationId)
                    .param("size", "200").param("entityType", entityType).param("kind", "CREATED"));
            assertThat(drill.get("page").get("totalElements").asLong()).isEqualTo(operation.get("recordCount").asLong());
            assertThat(drill.get("content")).isNotEmpty().allSatisfy(item ->
                    assertThat(item.get("entityType").asText()).isEqualTo(entityType));
        }
        JsonNode occurrenceListing = json(mockMvc, get("/v1/audit").param("size", "200").param("entityType", "Ocurrencia"));
        JsonNode transaction = entriesOfRevision(occurrenceListing, revision).getFirst();
        JsonNode revisionDrill = json(mockMvc, get("/v1/audit/revisions/{revision}", revision)
                .param("entityType", "Ocurrencia"));
        assertThat(revisionDrill.get("page").get("totalElements").asLong())
                .isEqualTo(transaction.get("recordCount").asLong()).isEqualTo(7);
    }

    @Test
    @DisplayName("filtra por tipo de entidad (etiqueta de dominio)")
    void filtersByEntityType() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(32));
        bumpSetting("21:30");

        // the entries are OPERATION (createRecurringEvent and the PUT carry @AuditOperation) and entityTypes
        // only counts the rows of the requested type
        mockMvc.perform(get("/v1/audit").param("size", "200").param("entityType", "Configuración"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[*].entityTypes[*]", everyItem(is("Configuración"))));

        String createOperation = latestOperationId("Alta de evento recurrente");
        JsonNode occurrences = json(mockMvc, get("/v1/audit").param("size", "200").param("entityType", "Ocurrencia"));
        JsonNode operation = operationEntry(occurrences, createOperation);
        assertThat(objectMapper.convertValue(operation.get("entityTypes"), List.class)).containsExactly("Ocurrencia");
        assertThat(operation.get("recordCount").asLong())
                .isEqualTo(operationRows("ocurrencia_aud", createOperation, null));
    }

    @Test
    @DisplayName("GET /v1/audit/revisions/{rev} inexistente responde 200 con página vacía")
    void revisionDrillDown_unknownRevision_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/v1/audit/revisions/{revision}", 999999999))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /v1/audit/revisions/{rev} con entityType desconocido o rango invertido responde 400")
    void revisionDrillDown_invalidFilters_return400() throws Exception {
        mockMvc.perform(get("/v1/audit/revisions/{revision}", 1).param("entityType", "NoExiste"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/audit/revisions/{revision}", 1)
                        .param("from", "2026-02-10").param("to", "2026-02-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un AUXILIAR_AULICO no puede consultar el detalle de revisión ni de operación (403)")
    void drillDownsForbiddenWithoutSubsecretariaRole() throws Exception {
        MockMvc auxMockMvc = mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO);

        auxMockMvc.perform(get("/v1/audit/revisions/{revision}", 1)).andExpect(status().isForbidden());
        auxMockMvc.perform(get("/v1/audit/operations/{operationId}", "x")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("entityType desconocido responde 400")
    void unknownEntityTypeReturns400() throws Exception {
        mockMvc.perform(get("/v1/audit").param("entityType", "NoExiste"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("filtra por usuario")
    void filtersByUser() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(33));

        mockMvc.perform(get("/v1/audit").param("size", "200").param("user", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[*].user", everyItem(is(USER))));
    }

    @Test
    @DisplayName("las filas vienen ordenadas por revisión descendente")
    void orderedByRevisionDescending() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(34));
        bumpSetting("21:15");

        MvcResult result = mockMvc.perform(get("/v1/audit").param("size", "200"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        int previous = Integer.MAX_VALUE;
        for (JsonNode row : content) {
            int revision = row.get("revision").asInt();
            assertThat(revision).isLessThanOrEqualTo(previous);
            previous = revision;
        }
    }

    @Test
    @DisplayName("un AUXILIAR_AULICO no puede consultar el registro (403)")
    void forbiddenWithoutSubsecretariaRole() throws Exception {
        MockMvc auxMockMvc = mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO);

        auxMockMvc.perform(get("/v1/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/audit/entity-types responde con las diez etiquetas de entidades auditadas")
    void entityTypesReturnsAllLabels() throws Exception {
        mockMvc.perform(get("/v1/audit/entity-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$", hasItem("Asignación de rol")))
                .andExpect(jsonPath("$", hasItem("Asignación de solicitud de aula")));
    }

    @Test
    @DisplayName("cada etiqueta de /v1/audit/entity-types es aceptada como entityType por GET /v1/audit")
    void entityTypesCatalogMatchesFilter() throws Exception {
        MvcResult catalog = mockMvc.perform(get("/v1/audit/entity-types"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode labels = objectMapper.readTree(catalog.getResponse().getContentAsString());

        for (JsonNode label : labels) {
            mockMvc.perform(get("/v1/audit").param("entityType", label.asText()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("un AUXILIAR_AULICO no puede consultar el catálogo de tipos de entidad (403)")
    void entityTypesForbiddenWithoutSubsecretariaRole() throws Exception {
        MockMvc auxMockMvc = mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO);

        auxMockMvc.perform(get("/v1/audit/entity-types"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("filtra por fragmento de usuario, sin importar mayúsculas")
    void filtersByUserPartialCaseInsensitive() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(37));

        mockMvc.perform(get("/v1/audit").param("size", "200").param("user", "INTEGRATION-TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[*].user", everyItem(is(USER))));
    }

    @Test
    @DisplayName("un fragmento de usuario que no matchea a nadie devuelve página vacía")
    void filtersByUserNoMatchReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/v1/audit").param("user", "nadie-que-exista"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("'to' en el pasado sin 'from' responde 200")
    void pastToWithoutFromReturns200() throws Exception {
        mockMvc.perform(get("/v1/audit").param("to", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isOk());
    }
}
