package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
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
                .andExpect(jsonPath("$.content[*].entityType", hasItem("Evento académico")))
                .andExpect(jsonPath("$.content[*].entityType", hasItem("Configuración")))
                .andExpect(jsonPath("$.content[*].entityType", hasItem("Ocurrencia")))
                // hay entradas CHANGE del seed; puede haber OPERATION de otros flujos que
                // compartan el contexto (Envers commitea sin rollback), así que no se exige
                // que TODAS sean CHANGE, sólo que el log unificado las incluya.
                .andExpect(jsonPath("$.content[*].type", hasItem("CHANGE")))
                .andExpect(jsonPath("$.content[*].description", everyItem(not(blankOrNullString()))));
    }

    @Test
    @DisplayName("un cambio suelto sin @AuditOperation trae una descripción derivada del tipo de cambio")
    void looseChangeHasTemplatedDescription() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(36));

        mockMvc.perform(get("/v1/audit").param("size", "200").param("entityType", "Evento académico")
                        .param("kind", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[*].description",
                        everyItem(startsWith("Alta de Evento académico"))));
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
    @DisplayName("filtra por tipo de cambio")
    void filtersByKind() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(31));

        mockMvc.perform(get("/v1/audit").param("size", "200").param("kind", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[*].kind", everyItem(is("CREATED"))));
    }

    @Test
    @DisplayName("filtra por tipo de entidad (etiqueta de dominio)")
    void filtersByEntityType() throws Exception {
        seedRecurringEvent(LocalDate.now().plusDays(32));
        bumpSetting("21:30");

        mockMvc.perform(get("/v1/audit").param("size", "200").param("entityType", "Configuración"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[*].entityType", everyItem(is("Configuración"))))
                .andExpect(jsonPath("$.content[*].entityType", not(hasItem("Evento académico"))));
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
