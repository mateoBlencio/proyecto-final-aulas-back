package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica la superficie pública del módulo: el alta y los catálogos tienen que
 * funcionar <b>sin</b> token, y el resto de {@code /v1/**} tiene que seguir cerrado.
 */
@Import(IntegrationTestData.class)
@DisplayName("Solicitudes de aula API (integración)")
class RoomRequestApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private ObjectMapper objectMapper;

    /** MockMvc sin header de Authorization, para probar los endpoints públicos. */
    private MockMvc anonymousMockMvc;

    @BeforeEach
    void setUpAnonymousMockMvc() {
        anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("POST sin autenticación: crea la solicitud y devuelve 201")
    void create_withoutAuthentication_returnsCreated() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building building = testData.edificio();
        Classroom classroom = testData.aula(building);

        Map<String, Object> body = Map.of(
                "type", "PARTIAL_EXAM",
                "scope", "GRADO",
                "teacherName", "Ada Lovelace",
                "teacherEmail", "ada@frc.utn.edu.ar",
                "teacherPhone", "351-1234567",
                "subjectId", academic.subjectId(),
                "items", java.util.List.of(Map.of(
                        "commissionId", academic.commissionId(),
                        "date", LocalDate.now().plusDays(7).toString(),
                        "startTime", "10:00:00",
                        "endTime", "12:00:00",
                        "enrolled", 30,
                        "estimated", 35,
                        "classroomCount", 1,
                        "requiresProjector", true,
                        "requiresComputers", false,
                        "preferredClassroomIds", java.util.List.of(classroom.getId()))));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].durationMinutes").value(120));
    }

    @Test
    @DisplayName("POST con payload inválido: 400 sin necesidad de autenticación")
    void create_withInvalidPayload_returnsBadRequest() throws Exception {
        Map<String, Object> body = Map.of(
                "type", "CONFERENCE",
                "scope", "EXTENSION",
                "teacherName", "",
                "teacherEmail", "no-es-un-email",
                "teacherPhone", "351-1234567",
                "items", java.util.List.of());

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("catálogos sin autenticación: responden 200")
    void catalogs_withoutAuthentication_returnOk() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        testData.aula(testData.edificio());

        anonymousMockMvc.perform(get("/v1/room-requests/catalog/specialties"))
                .andExpect(status().isOk());
        anonymousMockMvc.perform(get("/v1/room-requests/catalog/commissions")
                        .param("subjectId", String.valueOf(academic.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(academic.commissionId()));
        anonymousMockMvc.perform(get("/v1/room-requests/catalog/classrooms"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("el permitAll no abre el resto de /v1: un endpoint interno sigue dando 401 sin token")
    void otherEndpoints_remainAuthenticated() throws Exception {
        anonymousMockMvc.perform(get("/v1/classrooms"))
                .andExpect(status().isUnauthorized());
        anonymousMockMvc.perform(get("/v1/subjects"))
                .andExpect(status().isUnauthorized());
    }
}
