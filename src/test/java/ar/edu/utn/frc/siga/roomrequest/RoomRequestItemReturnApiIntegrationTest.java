package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/return (integración)")
class RoomRequestItemReturnApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Test
    @DisplayName("DERIVED_TO_BUILDING con motivo: vuelve a NEW y deja returnedFromBuildingId + returnedReason")
    void return_derivado_returnsPending() throws Exception {
        RoomRequestItem item = seedDerivedItem(9L);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"el laboratorio se bloqueó\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @DisplayName("sin reason: 400 Invalid room request")
    void return_sinReason_returnsBadRequest() throws Exception {
        RoomRequestItem item = seedDerivedItem(9L);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid room request"));
    }

    @Test
    @DisplayName("desde NEW: 409 Invalid room request transition")
    void return_desdePending_returnsConflict() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.NEW);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"motivo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid room request transition"));
    }

    @Test
    @DisplayName("id inexistente: 404")
    void return_idInexistente_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(post("/v1/room-requests/items/" + unknownId + "/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"motivo\"}"))
                .andExpect(status().isNotFound());
    }

    private RoomRequestItem seedDerivedItem(Long derivedBuildingId) {
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
                .status(RoomRequestStatus.DERIVED_TO_BUILDING)
                .derivedBuildingId(derivedBuildingId)
                .derivedAt(LocalDateTime.now())
                .decidedBy("subsecretaria@frc.utn.edu.ar")
                .decidedAt(LocalDateTime.now())
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }

    private RoomRequestItem seedItem(RoomRequestStatus status) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusDays(10), status);
        roomRequestRepository.save(request);
        return item;
    }
}
