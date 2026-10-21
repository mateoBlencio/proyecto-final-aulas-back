package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

@Import(IntegrationTestData.class)
@DisplayName("Dashboard - POST /v1/dashboard/reports/{month}/regeneration (integración)")
class DashboardRegenerationApiIntegrationTest extends DashboardIntegrationSupport {

    private static final String PATH = "/v1/dashboard/reports/2020-03/regeneration";
    private static final String BODY = "{\"reason\":\"Se corrigió una capacidad\"}";

    @Autowired
    private DashboardSnapshotService snapshotService;

    @BeforeEach
    @AfterEach
    void cleanSnapshots() {
        jdbc.update("DELETE FROM tablero_snapshot");
    }

    private static String bodyWith(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }

    private ResultActions regenerate(MockMvc mvc, String body) throws Exception {
        return mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<Long> rowIds(boolean current) {
        return jdbc.queryForList("SELECT id_snapshot FROM tablero_snapshot WHERE anio = 2020 AND mes = 3 AND vigente = ? "
                + "ORDER BY id_snapshot", Long.class, current);
    }

    @Test
    @DisplayName("regenerating keeps the previous rows as not current and stores the new ones with reason and author")
    void regenerationRetiresPreviousRows() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);
        Integer rowsPerGeneration = jdbc.queryForObject("SELECT COUNT(*) FROM tablero_snapshot WHERE anio = 2020", Integer.class);

        regenerate(mockMvc, BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regenerationReason").value("Se corrigió una capacidad"))
                .andExpect(jsonPath("$.generatedBy").value("integration-test@frc.utn.edu.ar"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tablero_snapshot WHERE anio = 2020 AND NOT vigente", Integer.class))
                .isEqualTo(rowsPerGeneration);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tablero_snapshot WHERE anio = 2020 AND vigente", Integer.class))
                .isEqualTo(rowsPerGeneration);
        mockMvc.perform(get("/v1/audit").param("q", "Regeneración del informe 2020-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value(
                        "Regeneración del informe 2020-03 del tablero: Se corrigió una capacidad"));
    }

    @Test
    @DisplayName("the previous version stays stored untouched and no longer current; the report shows the new one")
    void previousVersionIsKeptAsNotCurrent() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);
        List<Long> firstVersion = rowIds(true);
        String firstPayloads = jdbc.queryForObject(
                "SELECT string_agg(datos::text, '|' ORDER BY id_snapshot) FROM tablero_snapshot WHERE anio = 2020", String.class);
        JsonNode before = getJson(mockMvc, "/v1/dashboard/reports/2020-03");
        assertThat(before.get("regenerationReason").isNull()).isTrue();
        assertThat(before.get("generatedBy").asText()).isEqualTo("SISTEMA");

        Integer regenerationsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM regeneracion_tablero WHERE anio = 2020 AND mes = 3", Integer.class);

        regenerate(mockMvc, BODY).andExpect(status().isOk());

        assertThat(rowIds(false)).isEqualTo(firstVersion);
        assertThat(rowIds(true)).isNotEmpty().doesNotContainAnyElementsOf(firstVersion).hasSameSizeAs(firstVersion);
        String retiredPayloads = jdbc.queryForObject(
                "SELECT string_agg(datos::text, '|' ORDER BY id_snapshot) FROM tablero_snapshot "
                        + "WHERE anio = 2020 AND NOT vigente", String.class);
        assertThat(retiredPayloads).isEqualTo(firstPayloads);
        JsonNode after = getJson(mockMvc, "/v1/dashboard/reports/2020-03");
        assertThat(after.get("regenerationReason").asText()).isEqualTo("Se corrigió una capacidad");
        assertThat(after.get("generatedBy").asText()).isEqualTo("integration-test@frc.utn.edu.ar");
        assertThat(after.get("generatedAt").asText()).isNotEqualTo(before.get("generatedAt").asText());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM regeneracion_tablero WHERE anio = 2020 AND mes = 3", Integer.class))
                .isEqualTo(regenerationsBefore + 1);
    }

    @Test
    @DisplayName("the audit log lists one entry named after the operation, with the reason and the author")
    void auditLogHasTheOperationEntry() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);

        regenerate(mockMvc, bodyWith("Motivo único de auditoría")).andExpect(status().isOk());

        JsonNode content = getJson(mockMvc, "/v1/audit", "q", "Motivo único de auditoría").get("content");
        assertThat(content).hasSize(1);
        JsonNode entry = content.get(0);
        assertThat(entry.get("description").asText())
                .isEqualTo("Regeneración del informe 2020-03 del tablero: Motivo único de auditoría");
        assertThat(entry.get("user").asText()).isEqualTo("integration-test@frc.utn.edu.ar");
        assertThat(entry.get("actorType").asText()).isEqualTo("HUMAN");
    }

    @Test
    @DisplayName("a missing, null, empty or blank reason is 400 and leaves the snapshot untouched")
    void invalidReasonIsBadRequest() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);
        List<Long> before = rowIds(true);

        regenerate(mockMvc, "{}").andExpect(status().isBadRequest());
        regenerate(mockMvc, "{\"reason\":null}").andExpect(status().isBadRequest());
        regenerate(mockMvc, bodyWith("")).andExpect(status().isBadRequest());
        regenerate(mockMvc, bodyWith("  ")).andExpect(status().isBadRequest());

        assertThat(rowIds(true)).isEqualTo(before);
        assertThat(rowIds(false)).isEmpty();
    }

    @Test
    @DisplayName("a 500-character reason is accepted and a 501-character one is 400")
    void reasonLengthBoundary() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);

        regenerate(mockMvc, bodyWith("x".repeat(501))).andExpect(status().isBadRequest());
        assertThat(rowIds(false)).isEmpty();
        regenerate(mockMvc, bodyWith("x".repeat(500))).andExpect(status().isOk());
        assertThat(rowIds(false)).isNotEmpty();
    }

    @Test
    @DisplayName("blank reason is 400, current month is 422, month without snapshot is 404, auxiliar is 403")
    void validationsAndPermissions() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);

        mockMvc.perform(post("/v1/dashboard/reports/2020-03/regeneration")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/dashboard/reports/" + YearMonth.now() + "/regeneration")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/v1/dashboard/reports/2020-04/regeneration")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        mockMvcAsAuxiliar("aux-dashboard@frc.utn.edu.ar")
                .perform(post("/v1/dashboard/reports/2020-03/regeneration")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO, CONSULTA and a user without roles get 403 and the snapshot is not regenerated")
    void onlySubsecretariaMayRegenerate() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);
        List<Long> before = rowIds(true);
        Integer regenerationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM regeneracion_tablero", Integer.class);

        regenerate(auxiliarOf(building()), BODY).andExpect(status().isForbidden());
        regenerate(consulta(), BODY).andExpect(status().isForbidden());
        regenerate(userWithoutRoles(), BODY).andExpect(status().isForbidden());

        assertThat(rowIds(true)).isEqualTo(before);
        assertThat(rowIds(false)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM regeneracion_tablero", Integer.class))
                .isEqualTo(regenerationsBefore);
    }

    @Test
    @DisplayName("a building-restricted user sees only that the report was regenerated, not who did it nor why")
    void restrictedUserDoesNotSeeAuthorNorReason() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);
        regenerate(mockMvc, BODY).andExpect(status().isOk());

        JsonNode restricted = getJson(auxiliarOf(building()), "/v1/dashboard/reports/2020-03");
        JsonNode global = getJson(mockMvc, "/v1/dashboard/reports/2020-03");

        assertThat(restricted.get("regenerated").asBoolean()).isTrue();
        assertThat(restricted.get("generatedBy").isNull()).isTrue();
        assertThat(restricted.get("regenerationReason").isNull()).isTrue();
        assertThat(global.get("generatedBy").asText()).isEqualTo("integration-test@frc.utn.edu.ar");
        assertThat(global.get("regenerationReason").asText()).isEqualTo("Se corrigió una capacidad");
    }

    @Test
    @DisplayName("two simultaneous regenerations of the same month both succeed and leave one current version")
    void concurrentRegenerationsAreSerialized() throws Exception {
        snapshotService.generate(YearMonth.of(2020, 3), "SISTEMA", null);
        Integer rowsPerGeneration = jdbc.queryForObject("SELECT COUNT(*) FROM tablero_snapshot", Integer.class);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Integer>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    return regenerate(mockMvc, BODY).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<Integer> call : calls) {
                assertThat(call.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tablero_snapshot WHERE vigente", Integer.class))
                .isEqualTo(rowsPerGeneration);
    }

    @Test
    @DisplayName("a month too far from today answers 400 on the report and on the regeneration")
    void monthOutOfRangeIsRejected() throws Exception {
        mockMvc.perform(get("/v1/dashboard/reports/-999999-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/dashboard/reports/1990-01")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/dashboard/reports/1990-01/regeneration").contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isBadRequest());
    }
}
