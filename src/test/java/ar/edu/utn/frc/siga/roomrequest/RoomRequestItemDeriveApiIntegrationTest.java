package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
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
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/derive (integración)")
class RoomRequestItemDeriveApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Test
    @DisplayName("edificio activo, con auxiliar y aula libre: pasa a DERIVED_TO_BUILDING")
    void derive_ok() throws Exception {
        Building building = testData.edificio();
        testData.aula(building);
        mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
        RoomRequestItem item = seedItem(RoomRequestStatus.NEW);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/derive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingId\":" + building.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DERIVED_TO_BUILDING"));
    }

    @Test
    @DisplayName("edificio sin ninguna aula libre: 400 Invalid room request")
    void derive_sinAulasLibres_returnsBadRequest() throws Exception {
        Building building = testData.edificio();
        mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
        RoomRequestItem item = seedItem(RoomRequestStatus.NEW);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/derive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingId\":" + building.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid room request"));
    }

    @Test
    @DisplayName("edificio inactivo: 400 Invalid room request")
    void derive_edificioInactivo_returnsBadRequest() throws Exception {
        Building building = testData.edificio("Edificio-Inactivo", false);
        RoomRequestItem item = seedItem(RoomRequestStatus.NEW);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/derive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingId\":" + building.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid room request"));
    }

    @Test
    @DisplayName("edificio inexistente: 404")
    void derive_edificioInexistente_returnsNotFound() throws Exception {
        long unknownBuildingId = 999_999_000L + IntegrationTestData.nextSeq();
        RoomRequestItem item = seedItem(RoomRequestStatus.NEW);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/derive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingId\":" + unknownBuildingId + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("desde IN_EVALUATION: 409 Invalid room request transition")
    void derive_desdeEnEvaluacion_returnsConflict() throws Exception {
        Building building = testData.edificio();
        testData.aula(building);
        mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
        RoomRequestItem item = seedItem(RoomRequestStatus.IN_EVALUATION);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/derive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingId\":" + building.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid room request transition"));
    }

    @Test
    @DisplayName("sin buildingId: 400 Validación fallida")
    void derive_sinBuildingId_returnsBadRequest() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.NEW);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/derive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"));
    }

    @Test
    @DisplayName("id de ítem inexistente: 404")
    void derive_itemInexistente_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(post("/v1/room-requests/items/" + unknownId + "/derive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingId\":1}"))
                .andExpect(status().isNotFound());
    }

    private RoomRequestItem seedItem(RoomRequestStatus status) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(academic.commissionId())
                .date(LocalDate.now().plusDays(10))
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(120))
                .estimated(35)
                .classroomCount(1)
                .build();
        request.addItem(item);
        if (status != RoomRequestStatus.NEW) {
            item.decide(status, "subsecretaria@frc.utn.edu.ar", "motivo de prueba", LocalDateTime.now());
        }
        roomRequestRepository.save(request);
        return item;
    }
}
