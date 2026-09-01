package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.security.JwtService;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

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
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtService jwtService;

    private void seedRecurringEvent(LocalDate date) {
        var sc = testData.materiaYComision();
        var dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(USER, "", "ROLE_SUBSECRETARIA"));
        try {
            academicEventService.createRecurringEvent(dto);
        } finally {
            SecurityContextHolder.clearContext();
        }
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
                .andExpect(jsonPath("$.content[*].type", everyItem(is("CHANGE"))))
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
                .andExpect(jsonPath("$.totalElements").value(0));
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
        String auxToken = jwtService.generateAccessToken("auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        MockMvc auxMockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + auxToken))
                .build();

        auxMockMvc.perform(get("/v1/audit"))
                .andExpect(status().isForbidden());
    }
}
