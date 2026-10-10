package ar.edu.utn.frc.siga.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(IntegrationTestData.class)
@DisplayName("Dashboard - GET /v1/dashboard/room-requests (integración)")
class DashboardRoomRequestsApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a pending item shows up in the current year, marked partial")
    void pendingItemAppearsInCurrentYear() throws Exception {
        var sc = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.ONE_TIME_ROOM_CHANGE, sc.subjectId());
        testData.itemDePedido(request, sc.commissionId(), LocalDate.now().plusDays(30), RoomRequestStatus.NEW);
        roomRequestRepository.save(request);

        String body = mockMvc.perform(get("/v1/dashboard/room-requests"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode years = objectMapper.readTree(body).get("years");
        JsonNode current = years.get(years.size() - 1);
        assertThat(current.get("year").asInt()).isEqualTo(LocalDate.now().getYear());
        assertThat(current.get("partial").asBoolean()).isTrue();
        assertThat(current.get("campus").get("pending").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(current.get("unassignedBuilding").get("pending").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("an item resolved and one expired by the system show up in the current year")
    void resolvedAndExpiredItemsAreCounted() throws Exception {
        var sc = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.ONE_TIME_ROOM_CHANGE, sc.subjectId());
        var resolved = testData.itemDePedido(request, sc.commissionId(), LocalDate.now().plusDays(30), RoomRequestStatus.NEW);
        var expired = testData.itemDePedido(request, sc.commissionId(), LocalDate.now().plusDays(31), RoomRequestStatus.NEW);
        roomRequestRepository.save(request);
        jdbc.update("""
                UPDATE solicitud_aula_item SET estado = 'RESOLVED', decidido_por = 'x', fecha_decision = now(),
                       fecha_notificacion = now() WHERE id_item = ?""", resolved.getId());
        jdbc.update("""
                UPDATE solicitud_aula_item SET estado = 'CANCELLED', decidido_por = 'SISTEMA', fecha_decision = now()
                WHERE id_item = ?""", expired.getId());

        String body = mockMvc.perform(get("/v1/dashboard/room-requests"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode years = objectMapper.readTree(body).get("years");
        JsonNode current = years.get(years.size() - 1);
        assertThat(current.get("campus").get("expired").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(current.get("campus").get("resolved").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("years outside 1..10 returns 400")
    void yearsOutOfRangeIsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/dashboard/room-requests").param("years", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/dashboard/room-requests").param("years", "11")).andExpect(status().isBadRequest());
    }
}
