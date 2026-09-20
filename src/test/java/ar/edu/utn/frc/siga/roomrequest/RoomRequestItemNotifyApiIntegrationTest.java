package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/notify (integración)")
class RoomRequestItemNotifyApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Test
    @DisplayName("PRE_APPROVED con aula asignada: pasa a RESOLVED y sella notifiedAt")
    void notify_ok() throws Exception {
        RoomRequestItem item = seedPreApprovedItem(1);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("llamarlo dos veces devuelve 200 las dos veces con el mismo notifiedAt")
    void notify_idempotente() throws Exception {
        RoomRequestItem item = seedPreApprovedItem(1);

        MvcResult first = mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(first.getResponse().getContentAsString()).isEqualTo(second.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("estado distinto de PRE_APPROVED: 409")
    void notify_estadoInvalido_returnsConflict() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.PENDING);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid room request transition"));
    }

    @Test
    @DisplayName("sin ninguna aula asignada: 400")
    void notify_sinAulas_returnsBadRequest() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.PRE_APPROVED);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid room request"));
    }

    @Test
    @DisplayName("id inexistente: 404")
    void notify_idInexistente_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(post("/v1/room-requests/items/" + unknownId + "/notify"))
                .andExpect(status().isNotFound());
    }

    private RoomRequestItem seedPreApprovedItem(int classroomCount) {
        Long classroomId = testData.aula(testData.edificio()).getId();
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        LocalDate occurrenceDate = LocalDate.now().plusDays(29);
        var eventDto = new CreateRecurringEventRequestDto(30, LocalTime.of(9, 0), 90,
                occurrenceDate.getDayOfWeek(), occurrenceDate, occurrenceDate,
                academic.subjectId(), academic.commissionId());
        Long eventId = academicEventService.createRecurringEvent(eventDto).id();
        Long occurrenceId = occurrenceRepository.findByEvent_Id(eventId).getFirst().getId();
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.FINAL_EXAM)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .date(LocalDate.now().plusDays(30))
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(classroomCount)
                .status(RoomRequestStatus.PRE_APPROVED)
                .decidedBy("subsecretaria@frc.utn.edu.ar")
                .decidedAt(LocalDateTime.now())
                .build();
        item.assignClassroom(classroomId, List.of(occurrenceId));
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }

    private RoomRequestItem seedItem(RoomRequestStatus status) {
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
                .date(LocalDate.now().plusDays(31))
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(1)
                .build();
        request.addItem(item);
        if (status != RoomRequestStatus.PENDING) {
            item.decide(status, "subsecretaria@frc.utn.edu.ar", null, LocalDateTime.now());
        }
        roomRequestRepository.save(request);
        return item;
    }
}
