package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/confirm y /reopen (integración)")
class RoomRequestItemConfirmReopenApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private RoomRequestItemRepository itemRepository;

    private RoomRequestItem seed(RoomRequestStatus status, LocalDate date) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), date, RoomRequestStatus.NEW);
        item.decide(status, "subsecretaria@frc.utn.edu.ar", "motivo de prueba", Instant.now());
        roomRequestRepository.save(request);
        return item;
    }

    @Test
    @DisplayName("confirm: NOTIFIED pasa a RESOLVED y el detalle trae acceptedAt y acceptedBy")
    void confirm_ok() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.NOTIFIED, LocalDate.now().plusDays(10));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.acceptedBy").isNotEmpty())
                .andExpect(jsonPath("$.acceptanceDeadline").doesNotExist());

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("RESOLVED"))
                .andExpect(jsonPath("$.item.acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.allowedActions.length()").value(1))
                .andExpect(jsonPath("$.allowedActions[0]").value("REOPEN"));
    }

    @Test
    @DisplayName("confirm: un pedido que no está NOTIFIED da 409")
    void confirm_estadoInvalido() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.IN_EVALUATION, LocalDate.now().plusDays(10));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid room request transition"));
    }

    @Test
    @DisplayName("confirm: un auxiliar ajeno al edificio derivado recibe 403")
    void confirm_auxiliarAjeno() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.NOTIFIED, LocalDate.now().plusDays(10));
        Building own = testData.edificio();
        Building other = testData.edificio();
        item.deriveTo(own.getId(), "subsecretaria@frc.utn.edu.ar", Instant.now());
        item.decide(RoomRequestStatus.NOTIFIED, "subsecretaria@frc.utn.edu.ar", null, Instant.now());
        itemRepository.save(item);

        mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, other.getId())
                .perform(post("/v1/room-requests/items/" + item.getId() + "/confirm"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("confirm: el auxiliar del edificio derivado puede confirmar")
    void confirm_auxiliarDelEdificioDerivado() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.NEW, LocalDate.now().plusDays(10));
        Building building = testData.edificio();
        item.deriveTo(building.getId(), "subsecretaria@frc.utn.edu.ar", Instant.now());
        item.decide(RoomRequestStatus.NOTIFIED, "auxiliar@frc.utn.edu.ar", null, Instant.now());
        itemRepository.save(item);

        mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId())
                .perform(post("/v1/room-requests/items/" + item.getId() + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("reopen: NOTIFIED vuelve a IN_EVALUATION con el motivo, sin plazo ni aceptación")
    void reopen_desdeNotificado() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.NOTIFIED, LocalDate.now().plusDays(10));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/reopen")
                        .contentType("application/json")
                        .content("{\"reason\":\"La 204 queda lejos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"))
                .andExpect(jsonPath("$.decisionReason").value("La 204 queda lejos"))
                .andExpect(jsonPath("$.acceptanceDeadline").doesNotExist())
                .andExpect(jsonPath("$.acceptedAt").doesNotExist());
    }

    @Test
    @DisplayName("reopen: sin motivo da 400")
    void reopen_sinMotivo() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.NOTIFIED, LocalDate.now().plusDays(10));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/reopen")
                        .contentType("application/json")
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("reopen: RESOLVED con fecha futura se reabre")
    void reopen_resueltoConFechaFutura() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.RESOLVED, LocalDate.now().plusDays(5));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/reopen")
                        .contentType("application/json")
                        .content("{\"reason\":\"El proyector no anda\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"));
    }

    @Test
    @DisplayName("reopen: RESOLVED con fecha pasada da 400 y allowedActions no ofrece REOPEN")
    void reopen_resueltoConFechaPasada() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.RESOLVED, LocalDate.now().minusDays(2));

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(0));
        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/reopen")
                        .contentType("application/json")
                        .content("{\"reason\":\"Quiero otra aula\"}"))
                .andExpect(status().isBadRequest());
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.RESOLVED);
    }

    @Test
    @DisplayName("allowedActions de un NOTIFIED para subsecretaría: CANCEL, CONFIRM y REOPEN")
    void allowedActions_notificado() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.NOTIFIED, LocalDate.now().plusDays(10));

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(3))
                .andExpect(jsonPath("$.allowedActions[0]").value("CANCEL"))
                .andExpect(jsonPath("$.allowedActions[1]").value("CONFIRM"))
                .andExpect(jsonPath("$.allowedActions[2]").value("REOPEN"));
    }

    @Test
    @DisplayName("cancel: un NOTIFIED se puede cancelar")
    void cancel_desdeNotificado() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.NOTIFIED, LocalDate.now().plusDays(10));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/cancel")
                        .contentType("application/json")
                        .content("{\"reason\":\"Se suspendió el parcial\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("assign: un NOTIFIED se rechaza con 409 hasta que se reabra")
    void assign_notificadoSeRechaza() throws Exception {
        RoomRequestItem item = seed(RoomRequestStatus.NOTIFIED, LocalDate.now().plusDays(10));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType("application/json")
                        .content("{\"classroomIds\":[1]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Room request already notified"));
    }
}
