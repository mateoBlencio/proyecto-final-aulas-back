package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/cancel (integración)")
class RoomRequestItemCancelApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Test
    @DisplayName("NEW con motivo: pasa a CANCELLED")
    void cancel_pending_returnsCancelled() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.NEW);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"el docente se arrepintió\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.decisionReason").value("el docente se arrepintió"));
    }

    @Test
    @DisplayName("RESOLVED: 409 Room request already notified, es terminal")
    void cancel_resolved_returnsConflict() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.RESOLVED);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"se cayó el laboratorio\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Room request already notified"));
    }

    @Test
    @DisplayName("IN_EVALUATION: pasa a CANCELLED (aula asignada, docente sin avisar todavía)")
    void cancel_inEvaluation_returnsCancelled() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.IN_EVALUATION);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"se cayó el laboratorio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("sin reason: 400 Invalid room request")
    void cancel_sinReason_returnsBadRequest() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.NEW);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid room request"));
    }

    @Test
    @DisplayName("ya CANCELLED: 409 Invalid room request transition")
    void cancel_yaCancelado_returnsConflict() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.CANCELLED);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"otra vez\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid room request transition"));
    }

    @Test
    @DisplayName("id inexistente: 404")
    void cancel_idInexistente_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(post("/v1/room-requests/items/" + unknownId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"motivo\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token: 401; con AUXILIAR_AULICO sobre un NEW (todavía no es de ningún edificio): 403")
    void authenticationAndAuthorization() throws Exception {
        RoomRequestItem pendingParaAnonimo = seedItem(RoomRequestStatus.NEW);
        RoomRequestItem pendingParaAuxiliar = seedItem(RoomRequestStatus.NEW);

        MockMvc anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        anonymousMockMvc.perform(post("/v1/room-requests/items/" + pendingParaAnonimo.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"motivo\"}"))
                .andExpect(status().isUnauthorized());

        mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO)
                .perform(post("/v1/room-requests/items/" + pendingParaAuxiliar.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"motivo\"}"))
                .andExpect(status().isForbidden());
    }

    private RoomRequestItem seedItem(RoomRequestStatus status) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(10), status);
        roomRequestRepository.save(request);
        return item;
    }
}
