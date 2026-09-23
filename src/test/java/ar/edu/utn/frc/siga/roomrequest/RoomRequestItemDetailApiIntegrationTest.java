package ar.edu.utn.frc.siga.roomrequest;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("GET /v1/room-requests/items/{id} (integración)")
class RoomRequestItemDetailApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Test
    @DisplayName("id existente: devuelve la cabecera completa (con contacto del docente) y el ítem completo")
    void findById_returnsFullHeaderAndItem() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(10),
                RoomRequestStatus.IN_EVALUATION);
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.id").value(request.getId()))
                .andExpect(jsonPath("$.request.teacherEmail").value("ada@frc.utn.edu.ar"))
                .andExpect(jsonPath("$.request.teacherPhone").value("351-1234567"))
                .andExpect(jsonPath("$.item.id").value(item.getId()))
                .andExpect(jsonPath("$.item.status").value("IN_EVALUATION"))
                .andExpect(jsonPath("$.item.decidedBy").value("subsecretaria@frc.utn.edu.ar"))
                .andExpect(jsonPath("$.item.observations").doesNotExist())
                .andExpect(jsonPath("$.item.enrolled").doesNotExist())
                .andExpect(jsonPath("$.item.currentClassrooms").doesNotExist());
    }

    @Test
    @DisplayName("pedido devuelto por un edificio: viaja derivedBuilding (limpio) y returnedFromBuilding")
    void findById_returnedItem_showsReturnedFromBuilding() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building building = testData.edificio();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(11),
                RoomRequestStatus.NEW);
        item.deriveTo(building.getId(), "subsecretaria@frc.utn.edu.ar", LocalDateTime.now());
        item.returnFromBuilding("no había proyector");
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("NEW"))
                .andExpect(jsonPath("$.item.derivedBuilding").doesNotExist())
                .andExpect(jsonPath("$.item.returnedFromBuilding.id").value(building.getId()))
                .andExpect(jsonPath("$.item.returnedFromBuilding.name").value(building.getName()))
                .andExpect(jsonPath("$.item.returnedReason").value("no había proyector"));
    }

    @Test
    @DisplayName("id inexistente: 404")
    void findById_unknownId_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(get("/v1/room-requests/items/" + unknownId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token: 401; con AUXILIAR_AULICO de alcance GLOBAL: 200 (cubre cualquier edificio)")
    void authenticationAndAuthorization() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(10),
                RoomRequestStatus.NEW);
        roomRequestRepository.save(request);

        MockMvc anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        anonymousMockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isUnauthorized());

        mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO)
                .perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO acotado a un edificio: lee un pedido derivado a SU edificio, "
            + "403 sobre uno de otro edificio o uno todavía sin derivar")
    void findById_auxiliarScopedToBuilding_isRestrictedToOwnBuilding() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building suEdificio = testData.edificio();
        Building otroEdificio = testData.edificio();

        RoomRequest requestPropio = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem itemPropio = testData.itemDePedido(requestPropio, academic.commissionId(),
                LocalDate.now().plusDays(10), RoomRequestStatus.NEW);
        itemPropio.deriveTo(suEdificio.getId(), "subsecretaria@frc.utn.edu.ar", LocalDateTime.now());
        roomRequestRepository.save(requestPropio);

        RoomRequest requestAjeno = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem itemAjeno = testData.itemDePedido(requestAjeno, academic.commissionId(),
                LocalDate.now().plusDays(10), RoomRequestStatus.NEW);
        itemAjeno.deriveTo(otroEdificio.getId(), "subsecretaria@frc.utn.edu.ar", LocalDateTime.now());
        roomRequestRepository.save(requestAjeno);

        RoomRequest requestSinDerivar = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem itemSinDerivar = testData.itemDePedido(requestSinDerivar, academic.commissionId(),
                LocalDate.now().plusDays(10), RoomRequestStatus.NEW);
        roomRequestRepository.save(requestSinDerivar);

        MockMvc auxiliarMockMvc = mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, suEdificio.getId());

        auxiliarMockMvc.perform(get("/v1/room-requests/items/" + itemPropio.getId()))
                .andExpect(status().isOk());
        auxiliarMockMvc.perform(get("/v1/room-requests/items/" + itemAjeno.getId()))
                .andExpect(status().isForbidden());
        auxiliarMockMvc.perform(get("/v1/room-requests/items/" + itemSinDerivar.getId()))
                .andExpect(status().isForbidden());
    }
}
