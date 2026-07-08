package ar.edu.utn.frc.siga.allocation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("integration")
@Sql(scripts = "/allocation/integration/setup-unassigned.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/allocation/integration/cleanup-unassigned.sql",
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AcademicEventControllerIT {

    private static final String UNASSIGNED_URL = "/api/v1/events/unassigned";

    @Autowired
    WebTestClient webTestClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode getUnassigned(String queryParams) throws Exception {
        String responseBody = webTestClient.get()
                .uri(UNASSIGNED_URL + queryParams)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        return objectMapper.readTree(responseBody);
    }

    // ─── TC-UE-001 — Default (desde hoy) ──────────────────────────────────────

    @Test
    void tcUe001_default_onlyFutureScheduledOccurrences() throws Exception {
        JsonNode root = getUnassigned("");

        assertThat(root).hasSize(1);
        JsonNode group = root.get(0);
        assertThat(group.path("eventId").asLong()).isEqualTo(1);
        assertThat(group.path("eventType").asText()).isEqualTo("RECURRING");
        assertThat(group.path("subject").asText()).isEqualTo("Programación I");
        assertThat(group.path("commission").asText()).isEqualTo("K1234");

        JsonNode occurrences = group.path("unassignedOccurrences");
        assertThat(occurrences).hasSize(2);
        assertThat(occurrences.get(0).path("id").asLong()).isEqualTo(1);
        assertThat(occurrences.get(1).path("id").asLong()).isEqualTo(6);
    }

    // ─── TC-UE-002 — from en el pasado ────────────────────────────────────────

    @Test
    void tcUe002_pastFrom_includesPastScheduledOccurrence() throws Exception {
        String from = LocalDate.now().minusDays(10).toString();

        JsonNode root = getUnassigned("?from=" + from);

        JsonNode occurrences = root.get(0).path("unassignedOccurrences");
        assertThat(occurrences).hasSize(3);
        assertThat(occurrences.get(0).path("id").asLong()).isEqualTo(5);
    }

    // ─── TC-UE-003 — from + to ─────────────────────────────────────────────────

    @Test
    void tcUe003_fromAndTo_excludesOccurrencesOutOfRange() throws Exception {
        String from = LocalDate.now().toString();
        String to = LocalDate.now().plusDays(10).toString();

        JsonNode root = getUnassigned("?from=" + from + "&to=" + to);

        JsonNode occurrences = root.get(0).path("unassignedOccurrences");
        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.get(0).path("id").asLong()).isEqualTo(1);
    }

    // ─── TC-UE-004 — to < from -> 400 ──────────────────────────────────────────

    @Test
    void tcUe004_toBeforeFrom_returns400() {
        String from = LocalDate.now().plusDays(10).toString();
        String to = LocalDate.now().toString();

        webTestClient.get()
                .uri(UNASSIGNED_URL + "?from=" + from + "&to=" + to)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ─── TC-UE-005 — GET /v1/events/{id} no colisiona con /unassigned ─────────

    @Test
    void tcUe005_findById_stillWorks() {
        webTestClient.get()
                .uri("/api/v1/events/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1);
    }

    // ─── TC-UE-006 — assign pasa a ASSIGNED, cancel vuelve a SCHEDULED ────────

    @Test
    void tcUe006_assignThenCancel_occurrenceStatusRoundTrips() throws Exception {
        JsonNode beforeAssign = getUnassigned("");
        assertThat(beforeAssign.get(0).path("unassignedOccurrences")).hasSize(2);

        String assignBody = """
                {"classroomId": 1, "observation": "asignación de prueba"}""";
        byte[] assignResponse = webTestClient.post()
                .uri("/api/v1/allocations/occurrences/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(assignBody)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        JsonNode allocation = objectMapper.readTree(assignResponse);
        long allocationId = allocation.path("id").asLong();

        JsonNode afterAssign = getUnassigned("");
        JsonNode occurrencesAfterAssign = afterAssign.get(0).path("unassignedOccurrences");
        assertThat(occurrencesAfterAssign).hasSize(1);
        assertThat(occurrencesAfterAssign.get(0).path("id").asLong()).isEqualTo(6);

        webTestClient.delete()
                .uri("/api/v1/allocations/" + allocationId)
                .exchange()
                .expectStatus().isNoContent();

        JsonNode afterCancel = getUnassigned("");
        assertThat(afterCancel.get(0).path("unassignedOccurrences")).hasSize(2);
    }
}