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
    @DisplayName("GET /items como SUBSECRETARIA con un ítem derivado: [RETURN, CANCEL] por fila")
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
    @DisplayName("GET /items/{id} como SUBSECRETARIA con un ítem NEW: [ASSIGN, DERIVE, CANCEL]")
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
}
