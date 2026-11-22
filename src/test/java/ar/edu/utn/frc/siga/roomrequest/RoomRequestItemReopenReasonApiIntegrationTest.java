package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("reopen conserva el motivo de la asignación parcial y guarda su propio motivo (integración)")
class RoomRequestItemReopenReasonApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PARTIAL_REASON = "solo hay una disponible";
    private static final String REOPEN_REASON = "El docente pide otra aula";

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("asignar parcial con motivo, notificar y reabrir: motivo_decision intacto, motivo_reapertura guardado y ambos en la respuesta")
    void reabrirNoPisaElMotivoDeLaAsignacionParcial() throws Exception {
        RoomRequestItem item = notifiedPartialItem(LocalDate.now().plusDays(50));
        String itemUrl = "/v1/room-requests/items/" + item.getId();

        mockMvc.perform(get(itemUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("NOTIFIED"))
                .andExpect(jsonPath("$.item.decisionReason").value(PARTIAL_REASON))
                .andExpect(jsonPath("$.item.reopenReason").doesNotExist());

        mockMvc.perform(post(itemUrl + "/reopen").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + REOPEN_REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"))
                .andExpect(jsonPath("$.decisionReason").value(PARTIAL_REASON))
                .andExpect(jsonPath("$.reopenReason").value(REOPEN_REASON));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select motivo_decision, motivo_reapertura from solicitud_aula_item where id_item = ?", item.getId());
        assertThat(row.get("motivo_decision")).isEqualTo(PARTIAL_REASON);
        assertThat(row.get("motivo_reapertura")).isEqualTo(REOPEN_REASON);

        mockMvc.perform(get(itemUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.decisionReason").value(PARTIAL_REASON))
                .andExpect(jsonPath("$.item.reopenReason").value(REOPEN_REASON));
    }

    @Test
    @DisplayName("la revisión de Envers del reopen guarda motivo_reapertura en solicitud_aula_item_aud y conserva motivo_decision")
    void reabrirQuedaAuditadoEnLaTablaAud() throws Exception {
        RoomRequestItem item = notifiedPartialItem(LocalDate.now().plusDays(51));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/reopen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + REOPEN_REASON + "\"}"))
                .andExpect(status().isOk());

        Map<String, Object> latest = jdbcTemplate.queryForMap(
                "select motivo_decision, motivo_reapertura, revtype from solicitud_aula_item_aud "
                        + "where id_item = ? order by rev desc limit 1", item.getId());
        assertThat(latest.get("motivo_reapertura")).isEqualTo(REOPEN_REASON);
        assertThat(latest.get("motivo_decision")).isEqualTo(PARTIAL_REASON);
        assertThat(((Number) latest.get("revtype")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("el mail de la segunda ronda de un reabierto con menos aulas muestra el motivo de la asignación parcial, no el de la reapertura")
    void mailDeLaSegundaRondaUsaElMotivoDeLaAsignacion() throws Exception {
        RoomRequestItem item = notifiedPartialItem(LocalDate.now().plusDays(52));
        String itemUrl = "/v1/room-requests/items/" + item.getId();

        mockMvc.perform(post(itemUrl + "/reopen").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + REOPEN_REASON + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(itemUrl + "/notify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));

        List<String> bodies = jdbcTemplate.queryForList(
                "select cuerpo from notificacion where clave_idempotencia = ?", String.class,
                "room-request-item:" + item.getId() + ":NOTIFIED:2");
        assertThat(bodies).hasSize(1);
        assertThat(bodies.getFirst()).contains(PARTIAL_REASON).doesNotContain(REOPEN_REASON);
    }

    /** Pide 2 aulas, asigna 1 con motivo y notifica: queda NOTIFIED con decisionReason de la asignación parcial. */
    private RoomRequestItem notifiedPartialItem(LocalDate date) throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        RoomRequestItem item = seedItem(date, 2);
        String itemUrl = "/v1/room-requests/items/" + item.getId();

        mockMvc.perform(post(itemUrl + "/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classroomIds\":[" + aula.getId() + "],\"reason\":\"" + PARTIAL_REASON + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(itemUrl + "/notify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));
        return item;
    }

    private RoomRequestItem seedItem(LocalDate date, int classroomCount) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.FINAL_EXAM)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date)
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(classroomCount)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }
}
