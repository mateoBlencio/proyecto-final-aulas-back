package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/suggestion (integración)")
class RoomRequestSuggestionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    @DisplayName("sugerir y confirmar: el pedido queda IN_EVALUATION y la asignación persiste con origen AUTOMATIC")
    void sugerirYConfirmar_asignaConOrigenAutomatico() throws Exception {
        testData.aula(testData.edificio());
        RoomRequestItem item = seedItem(60);

        String suggestionBody = mockMvc.perform(post(suggestionUrl(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUGGESTED"))
                .andExpect(jsonPath("$.itemId").value(item.getId()))
                .andExpect(jsonPath("$.classrooms.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String suggestionId = JsonPath.read(suggestionBody, "$.suggestionId");
        Integer suggestedClassroomId = JsonPath.read(suggestionBody, "$.classrooms[0].id");

        mockMvc.perform(post(suggestionUrl(item) + "/" + suggestionId + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"))
                .andExpect(jsonPath("$.assignedClassrooms[0].id").value(suggestedClassroomId));

        List<String> sources = jdbcTemplate.queryForList(
                "select a.origen from asignacion_aula a join solicitud_item_asignacion s "
                        + "on s.id_ocurrencia = a.id_ocurrencia where s.id_item = ?", String.class, item.getId());
        assertThat(sources).containsExactly("AUTOMATIC");
        assertThat(statusOf(item)).isEqualTo(RoomRequestStatus.IN_EVALUATION.name());
    }

    @Test
    @DisplayName("confirmar dos veces la misma sugerencia: 410 la segunda")
    void confirmarDosVeces_laSegundaDevuelveGone() throws Exception {
        testData.aula(testData.edificio());
        RoomRequestItem item = seedItem(61);
        String suggestionId = suggest(item);

        mockMvc.perform(post(suggestionUrl(item) + "/" + suggestionId + "/confirm"))
                .andExpect(status().isOk());
        mockMvc.perform(post(suggestionUrl(item) + "/" + suggestionId + "/confirm"))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("confirmar con un suggestionId inexistente: 410")
    void confirmarConIdInexistente_devuelveGone() throws Exception {
        RoomRequestItem item = seedItem(62);

        mockMvc.perform(post(suggestionUrl(item) + "/sug_inexistente/confirm"))
                .andExpect(status().isGone());
        assertThat(statusOf(item)).isEqualTo(RoomRequestStatus.NEW.name());
    }

    @Test
    @DisplayName("confirmar una sugerencia en la URL de otro pedido: 410 y el pedido original sigue sin asignar")
    void confirmarEnOtroPedido_devuelveGone() throws Exception {
        testData.aula(testData.edificio());
        RoomRequestItem item = seedItem(63);
        RoomRequestItem other = seedItem(64);
        String suggestionId = suggest(item);

        mockMvc.perform(post(suggestionUrl(other) + "/" + suggestionId + "/confirm"))
                .andExpect(status().isGone());
        assertThat(statusOf(other)).isEqualTo(RoomRequestStatus.NEW.name());
    }

    @Test
    @DisplayName("sugerir sin body (es opcional): 200")
    void sugerirSinBody_devuelveOk() throws Exception {
        testData.aula(testData.edificio());
        RoomRequestItem item = seedItem(65);

        mockMvc.perform(post(suggestionUrl(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(item.getId()));
    }

    @Test
    @DisplayName("sugerir con aulas excluidas: la excluida no vuelve a sugerirse")
    void sugerirExcluyendoAula_noLaSugiere() throws Exception {
        testData.aula(testData.edificio());
        RoomRequestItem item = seedItem(66);
        String first = mockMvc.perform(post(suggestionUrl(item)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer firstClassroomId = JsonPath.read(first, "$.classrooms[0].id");

        String second = mockMvc.perform(post(suggestionUrl(item))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"excludedClassroomIds\":[" + firstClassroomId + "]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Integer> secondIds = JsonPath.read(second, "$.classrooms[*].id");
        assertThat(secondIds).doesNotContain(firstClassroomId);
    }

    @Test
    @DisplayName("sugerir para un pedido inexistente: 404")
    void sugerirPedidoInexistente_devuelveNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(post("/v1/room-requests/items/" + unknownId + "/suggestion"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token: 401; con AUXILIAR_AULICO sobre un NEW: 403, en sugerir y en confirmar")
    void authenticationAndAuthorization() throws Exception {
        testData.aula(testData.edificio());
        RoomRequestItem item = seedItem(67);
        String suggestionId = suggest(item);
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        MockMvc auxiliar = mockMvcAs("auxiliar-sugerencias@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO);

        anonymous.perform(post(suggestionUrl(item))).andExpect(status().isUnauthorized());
        anonymous.perform(post(suggestionUrl(item) + "/sug_x/confirm")).andExpect(status().isUnauthorized());
        auxiliar.perform(post(suggestionUrl(item))).andExpect(status().isForbidden());
        auxiliar.perform(post(suggestionUrl(item) + "/" + suggestionId + "/confirm")).andExpect(status().isForbidden());
    }

    private String suggest(RoomRequestItem item) throws Exception {
        String body = mockMvc.perform(post(suggestionUrl(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUGGESTED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.suggestionId");
    }

    private static String suggestionUrl(RoomRequestItem item) {
        return "/v1/room-requests/items/" + item.getId() + "/suggestion";
    }

    private String statusOf(RoomRequestItem item) {
        return jdbcTemplate.queryForObject(
                "select estado from solicitud_aula_item where id_item = ?", String.class, item.getId());
    }

    private RoomRequestItem seedItem(int daysAhead) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.FINAL_EXAM, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(),
                LocalDate.now().plusDays(300L + daysAhead), RoomRequestStatus.NEW);
        roomRequestRepository.save(request);
        return item;
    }
}
