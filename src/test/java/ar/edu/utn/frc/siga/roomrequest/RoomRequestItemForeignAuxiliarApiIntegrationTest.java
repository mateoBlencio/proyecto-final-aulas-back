package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un auxiliar ajeno al edificio derivado recibe 403 antes que el 409 de estado, y no se entera del estado del ítem. */
@Import(IntegrationTestData.class)
@DisplayName("auxiliar ajeno al edificio derivado en assign/cancel/derive/return (integración)")
class RoomRequestItemForeignAuxiliarApiIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/v1/room-requests/items/";

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private RoomRequestItemRepository itemRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Test
    @DisplayName("assign sobre un ítem NOTIFIED de otro edificio: 403, no 409 ya notificado")
    void assign_ajenoSobreNotificado_403() throws Exception {
        Building own = testData.edificio();
        RoomRequestItem item = derivedItem(RoomRequestStatus.NOTIFIED, own);

        auxiliarOf(testData.edificio()).perform(post(BASE + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"classroomIds\":[1]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cancel sobre un ítem NOTIFIED de otro edificio: 403 y el ítem queda NOTIFIED")
    void cancel_ajenoSobreNotificado_403() throws Exception {
        Building own = testData.edificio();
        RoomRequestItem item = derivedItem(RoomRequestStatus.NOTIFIED, own);

        auxiliarOf(testData.edificio()).perform(post(BASE + item.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"intento ajeno\"}"))
                .andExpect(status().isForbidden());

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomRequestStatus.NOTIFIED);
    }

    @Test
    @DisplayName("cancel sobre un ítem RESOLVED de otro edificio: 403, no 409 ya notificado")
    void cancel_ajenoSobreResuelto_403() throws Exception {
        Building own = testData.edificio();
        RoomRequestItem item = derivedItem(RoomRequestStatus.RESOLVED, own);

        auxiliarOf(testData.edificio()).perform(post(BASE + item.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"intento ajeno\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("derive sobre un ítem NOTIFIED de otro edificio: 403, no 409 de transición")
    void derive_ajenoSobreNotificado_403() throws Exception {
        Building own = testData.edificio();
        Building target = testData.edificio();
        RoomRequestItem item = derivedItem(RoomRequestStatus.NOTIFIED, own);

        auxiliarOf(testData.edificio()).perform(post(BASE + item.getId() + "/derive")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"buildingId\":" + target.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("return sobre un ítem NOTIFIED de otro edificio: 403, no 409 de transición")
    void return_ajenoSobreNotificado_403() throws Exception {
        Building own = testData.edificio();
        RoomRequestItem item = derivedItem(RoomRequestStatus.NOTIFIED, own);

        auxiliarOf(testData.edificio()).perform(post(BASE + item.getId() + "/return")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"intento ajeno\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("regresión: el auxiliar del edificio derivado que asigna sobre un NOTIFIED sigue recibiendo 409 ya notificado")
    void assign_duenoSobreNotificado_409() throws Exception {
        Building own = testData.edificio();
        RoomRequestItem item = derivedItem(RoomRequestStatus.NOTIFIED, own);

        auxiliarOf(own).perform(post(BASE + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"classroomIds\":[1]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Room request already notified"));
    }

    @Test
    @DisplayName("regresión: el auxiliar del edificio derivado que cancela un RESOLVED sigue recibiendo 409 ya notificado")
    void cancel_duenoSobreResuelto_409() throws Exception {
        Building own = testData.edificio();
        RoomRequestItem item = derivedItem(RoomRequestStatus.RESOLVED, own);

        auxiliarOf(own).perform(post(BASE + item.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"intento propio\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Room request already notified"));
    }

    @Test
    @DisplayName("un usuario con ADMINISTRADOR y además auxiliar de otro edificio lee un ítem derivado a un tercero y ve su acción de subsecretaría")
    void subsecretariaYAuxiliarDeOtroEdificio_noSeRecorta() throws Exception {
        Building own = testData.edificio();
        Building auxiliarBuilding = testData.edificio();
        RoomRequestItem item = derivedItem(RoomRequestStatus.NOTIFIED, own);
        String email = "sub-aux-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar";
        MockMvc both = mockMvcAsScoped(email, SystemRole.ADMINISTRADOR, ScopeType.GLOBAL, null);
        roleAssignmentRepository.save(RoleAssignment.builder()
                .user(userRepository.findByEmailAndEnabledTrue(email).orElseThrow())
                .role(SystemRole.AUXILIAR_AULICO)
                .scopeType(ScopeType.BUILDING)
                .scopeId(auxiliarBuilding.getId())
                .build());

        both.perform(get(BASE + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("NOTIFIED"))
                .andExpect(jsonPath("$.allowedActions[0]").value("TAKE_OVER"));
    }

    private MockMvc auxiliarOf(Building building) {
        return mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
    }

    private RoomRequestItem derivedItem(RoomRequestStatus status, Building building) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(),
                LocalDate.now().plusDays(10), RoomRequestStatus.NEW);
        item.deriveTo(building.getId(), "subsecretaria@frc.utn.edu.ar", Instant.now());
        item.decide(status, "subsecretaria@frc.utn.edu.ar", "motivo de prueba", Instant.now());
        roomRequestRepository.save(request);
        return item;
    }
}
