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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Superficie pública del módulo: el alta (cuerpo polimórfico por {@code type}) y los catálogos tienen
 * que funcionar <b>sin</b> token, y el resto de {@code /v1/**} tiene que seguir cerrado.
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
        Classroom classroom = testData.aula(testData.edificio());

        Map<String, Object> body = validRequest(academic, item ->
                item.put("preferredClassroomIds", List.of(classroom.getId())));

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
                "requester", Map.of(
                        "scope", "EXTENSION",
                        "teacherName", "",
                        "teacherEmail", "no-es-un-email",
                        "teacherPhone", "351-1234567"),
                "items", List.of());

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("type desconocido: 400")
    void create_withUnknownType_returnsBadRequest() throws Exception {
        Map<String, Object> body = Map.of(
                "type", "NO_EXISTE",
                "requester", requester(),
                "items", List.of());

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("booleanos ausentes en el JSON: se toman como 'no' y no rompen la deserialización")
    void create_withOmittedBooleans_returnsCreated() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        Map<String, Object> body = validRequest(academic, item -> {
            item.remove("requiresProjector");
            item.remove("requiresComputers");
            item.remove("preferredClassroomIds");
        });

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].requiresProjector").value(false))
                .andExpect(jsonPath("$.items[0].requiresComputers").value(false))
                .andExpect(jsonPath("$.items[0].preferredClassrooms").isEmpty());
    }

    @Test
    @DisplayName("hora de fin anterior al inicio: 400")
    void create_withInvertedTimeRange_returnsBadRequest() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        Map<String, Object> body = validRequest(academic, item -> {
            item.put("startTime", "12:00:00");
            item.put("endTime", "10:00:00");
        });

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("aula repetida en las preferencias: 400")
    void create_withRepeatedPreference_returnsBadRequest() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Classroom classroom = testData.aula(testData.edificio());

        Map<String, Object> body = validRequest(academic, item ->
                item.put("preferredClassroomIds", List.of(classroom.getId(), classroom.getId())));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("pide computadoras sin decir cuántas: 400")
    void create_withComputersButNoCount_returnsBadRequest() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        Map<String, Object> body = validRequest(academic, item -> item.put("requiresComputers", true));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("regla de negocio violada (usuarios de examen en un parcial sin computadoras): 400")
    void create_withExamUsersButNoComputers_returnsBadRequest() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        Map<String, Object> body = validRequest(academic, item -> item.put("requiresExamUsers", true));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("parcial con computadoras sin responder si necesita usuarios de examen: 400")
    void create_examWithComputersAndNoExamUsersAnswer_returnsBadRequest() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        Map<String, Object> body = validRequest(academic, item -> {
            item.put("requiresComputers", true);
            item.put("computerCount", 20);
        });

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("parcial con computadoras respondiendo que no necesita usuarios de examen: 201")
    void create_examWithComputersAndFalseExamUsers_returnsCreated() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        Map<String, Object> body = validRequest(academic, item -> {
            item.put("requiresComputers", true);
            item.put("computerCount", 20);
            item.put("requiresExamUsers", false);
        });

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].requiresExamUsers").value(false));
    }

    @Test
    @DisplayName("observations de más de 1000 caracteres: 400 y no un 500 al insertar")
    void create_withOversizedObservations_returnsBadRequest() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        Map<String, Object> body = validRequest(academic, item -> item.put("observations", "x".repeat(1001)));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("observations largas pero dentro del límite: 201 y se guardan enteras")
    void create_withLongButValidObservations_returnsCreated() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        String longText = "x".repeat(1000);

        Map<String, Object> body = validRequest(academic, item -> item.put("observations", longText));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].observations").value(longText));
    }

    @Test
    @DisplayName("aula preferida inexistente: 404")
    void create_withUnknownClassroom_returnsNotFound() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();

        Map<String, Object> body = validRequest(academic, item ->
                item.put("preferredClassroomIds", List.of(999_999)));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("tipo OTHER sin observations: 400")
    void create_otherTypeWithoutObservations_returnsBadRequest() throws Exception {
        Map<String, Object> body = otherRequest(item -> {});

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("tipo OTHER con observations: 201, sin materia ni comisión")
    void create_otherTypeWithObservations_returnsCreated() throws Exception {
        Map<String, Object> body = otherRequest(item ->
                item.put("observations", "Necesito un aula para grabar un video institucional"));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OTHER"))
                .andExpect(jsonPath("$.subject").doesNotExist())
                .andExpect(jsonPath("$.items[0].commission").doesNotExist());
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
        anonymousMockMvc.perform(get("/v1/room-requests/catalog/subjects")
                        .param("specialtyCode", "1"))
                .andExpect(status().isOk());
        anonymousMockMvc.perform(get("/v1/room-requests/catalog/commission-schedule")
                        .param("subjectId", String.valueOf(academic.subjectId()))
                        .param("commissionId", String.valueOf(academic.commissionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots").isArray())
                .andExpect(jsonPath("$.dates").isArray());
    }

    @Test
    @DisplayName("catálogo de comisiones de una materia inexistente: 404")
    void commissionsCatalog_withUnknownSubject_returnsNotFound() throws Exception {
        anonymousMockMvc.perform(get("/v1/room-requests/catalog/commissions")
                        .param("subjectId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el permitAll no abre el resto de /v1: un endpoint interno sigue dando 401 sin token")
    void otherEndpoints_remainAuthenticated() throws Exception {
        anonymousMockMvc.perform(get("/v1/classrooms"))
                .andExpect(status().isUnauthorized());
        anonymousMockMvc.perform(get("/v1/subjects"))
                .andExpect(status().isUnauthorized());
    }

    private static Map<String, Object> requester() {
        Map<String, Object> requester = new LinkedHashMap<>();
        requester.put("scope", "GRADO");
        requester.put("teacherName", "Ada Lovelace");
        requester.put("teacherEmail", "ada@frc.utn.edu.ar");
        requester.put("teacherPhone", "351-1234567");
        return requester;
    }

    /** Parcial fuera de horario válido; el {@code mutation} rompe sólo el ítem que a cada test le interesa. */
    private Map<String, Object> validRequest(IntegrationTestData.SubjectAndCommission academic,
                                             Consumer<Map<String, Object>> mutation) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("commissionId", academic.commissionId());
        item.put("date", LocalDate.now().plusDays(7).toString());
        item.put("startTime", "10:00:00");
        item.put("endTime", "12:00:00");
        item.put("estimated", 35);
        item.put("classroomCount", 1);
        item.put("requiresProjector", true);
        item.put("requiresComputers", false);
        item.put("preferredClassroomIds", List.of());
        mutation.accept(item);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "PARTIAL_EXAM_OFF_SCHEDULE");
        body.put("requester", requester());
        body.put("subjectId", academic.subjectId());
        body.put("items", List.of(item));
        return body;
    }

    private Map<String, Object> otherRequest(Consumer<Map<String, Object>> mutation) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("date", LocalDate.now().plusDays(7).toString());
        item.put("startTime", "10:00:00");
        item.put("endTime", "12:00:00");
        item.put("estimated", 35);
        item.put("classroomCount", 1);
        item.put("preferredClassroomIds", List.of());
        mutation.accept(item);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "OTHER");
        body.put("requester", requester());
        body.put("items", List.of(item));
        return body;
    }
}
