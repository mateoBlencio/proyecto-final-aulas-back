package ar.edu.utn.frc.siga.roomrequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(IntegrationTestData.class)
@DisplayName("allowedActions en la bandeja y el detalle de ítems (integración)")
class RoomRequestItemAllowedActionsApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    private MockMvc auxiliarOf(Building building) {
        return mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
    }

    private RoomRequestItem derivedItem(IntegrationTestData.SubjectAndCommission academic, Building building) {
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(),
                LocalDate.now().plusDays(10), RoomRequestStatus.NEW);
        item.deriveTo(building.getId(), "subsecretaria@frc.utn.edu.ar", Instant.now());
        roomRequestRepository.save(request);
        return item;
    }

    @Test
    @DisplayName("GET /items como auxiliar del edificio A con un ítem derivado a A: [ASSIGN, RETURN, CANCEL]")
    void listAsAuxiliarOfDerivedBuilding() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building a = testData.edificio();
        RoomRequestItem item = derivedItem(academic, a);

        auxiliarOf(a).perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].itemId").value(item.getId()))
                .andExpect(jsonPath("$.content[0].allowedActions.length()").value(3))
                .andExpect(jsonPath("$.content[0].allowedActions[0]").value("ASSIGN"))
                .andExpect(jsonPath("$.content[0].allowedActions[1]").value("RETURN"))
                .andExpect(jsonPath("$.content[0].allowedActions[2]").value("CANCEL"));
    }

    @Test
    @DisplayName("GET /items como ADMINISTRADOR con un ítem derivado: [RETURN, CANCEL] por fila")
    void listAsSubsecretariaOnDerivedItem() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        derivedItem(academic, testData.edificio());

        mockMvc.perform(get("/v1/room-requests/items")
                        .param("subjectId", String.valueOf(academic.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].allowedActions.length()").value(2))
                .andExpect(jsonPath("$.content[0].allowedActions[0]").value("RETURN"))
                .andExpect(jsonPath("$.content[0].allowedActions[1]").value("CANCEL"));
    }

    @Test
    @DisplayName("GET /items/{id} como ADMINISTRADOR con un ítem NEW: [ASSIGN, DERIVE, CANCEL]")
    void detailAsSubsecretariaOnNewItem() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(),
                LocalDate.now().plusDays(10), RoomRequestStatus.NEW);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(3))
                .andExpect(jsonPath("$.allowedActions[0]").value("ASSIGN"))
                .andExpect(jsonPath("$.allowedActions[1]").value("DERIVE"))
                .andExpect(jsonPath("$.allowedActions[2]").value("CANCEL"));
    }

    @Test
    @DisplayName("GET /items/{id} como auxiliar del edificio derivado: [ASSIGN, RETURN, CANCEL]")
    void detailAsAuxiliarOfDerivedBuilding() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building a = testData.edificio();
        RoomRequestItem item = derivedItem(academic, a);

        auxiliarOf(a).perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(3))
                .andExpect(jsonPath("$.allowedActions[0]").value("ASSIGN"))
                .andExpect(jsonPath("$.allowedActions[1]").value("RETURN"))
                .andExpect(jsonPath("$.allowedActions[2]").value("CANCEL"));
    }

    private RoomRequestItem derivedItemIn(RoomRequestStatus status, LocalDate date, Building building) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), date, RoomRequestStatus.NEW);
        item.deriveTo(building.getId(), "subsecretaria@frc.utn.edu.ar", Instant.now());
        item.decide(status, "auxiliar@frc.utn.edu.ar", null, Instant.now());
        roomRequestRepository.save(request);
        return item;
    }

    @Test
    @DisplayName("GET /items/{id} IN_EVALUATION derivado: subsecretaría ve [TAKE_OVER, CANCEL] y el auxiliar [ASSIGN, CANCEL, NOTIFY]")
    void detailInEvaluationDerivedOffersTakeOverOnlyToSubsecretaria() throws Exception {
        Building a = testData.edificio();
        RoomRequestItem item = derivedItemIn(RoomRequestStatus.IN_EVALUATION, LocalDate.now().plusDays(10), a);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(2))
                .andExpect(jsonPath("$.allowedActions[0]").value("TAKE_OVER"))
                .andExpect(jsonPath("$.allowedActions[1]").value("CANCEL"));
        auxiliarOf(a).perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(3))
                .andExpect(jsonPath("$.allowedActions[0]").value("ASSIGN"))
                .andExpect(jsonPath("$.allowedActions[1]").value("CANCEL"))
                .andExpect(jsonPath("$.allowedActions[2]").value("NOTIFY"));
    }

    @Test
    @DisplayName("GET /items/{id} NOTIFIED derivado como subsecretaría: [TAKE_OVER]")
    void detailNotifiedDerivedOffersTakeOver() throws Exception {
        RoomRequestItem item = derivedItemIn(RoomRequestStatus.NOTIFIED, LocalDate.now().plusDays(10), testData.edificio());

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions.length()").value(1))
                .andExpect(jsonPath("$.allowedActions[0]").value("TAKE_OVER"));
    }

    @Test
    @DisplayName("GET /items/{id} RESOLVED derivado: TAKE_OVER con fecha futura, ninguna acción con fecha pasada")
    void detailResolvedDerivedHidesTakeOverWithPastDate() throws Exception {
        Building building = testData.edificio();
        RoomRequestItem future = derivedItemIn(RoomRequestStatus.RESOLVED, LocalDate.now().plusDays(10), building);
        RoomRequestItem past = derivedItemIn(RoomRequestStatus.RESOLVED, LocalDate.now().minusDays(1), building);

        mockMvc.perform(get("/v1/room-requests/items/" + future.getId()))
                .andExpect(jsonPath("$.allowedActions.length()").value(1))
                .andExpect(jsonPath("$.allowedActions[0]").value("TAKE_OVER"));
        mockMvc.perform(get("/v1/room-requests/items/" + past.getId()))
                .andExpect(jsonPath("$.allowedActions.length()").value(0));
    }
}
