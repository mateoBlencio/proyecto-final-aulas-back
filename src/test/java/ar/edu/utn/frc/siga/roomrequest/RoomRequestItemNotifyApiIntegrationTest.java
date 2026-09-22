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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

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
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("IN_EVALUATION con aula asignada: pasa a RESOLVED y sella notifiedAt")
    void notify_ok() throws Exception {
        RoomRequestItem item = seedPreApprovedItem(1);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("notify exitoso: deja una única notificación al docente en la tabla notificacion")
    void notify_ok_notificaAlDocente() throws Exception {
        RoomRequestItem item = seedPreApprovedItem(1);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = notificationRows(item.getId());
        assertThat(rows).hasSize(1);
        Map<String, Object> notification = rows.getFirst();
        assertThat(notification.get("destinatario")).isEqualTo("ada@frc.utn.edu.ar");
        assertThat(notification.get("plantilla")).isEqualTo("ROOM_REQUEST_RESOLVED");
        assertThat((String) notification.get("asunto")).startsWith("Aula confirmada");
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
        assertThat(notificationRows(item.getId())).hasSize(1);
    }

    @Test
    @DisplayName("estado distinto de IN_EVALUATION: 409")
    void notify_estadoInvalido_returnsConflict() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.NEW);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid room request transition"));
    }

    @Test
    @DisplayName("sin ninguna aula asignada: 400")
    void notify_sinAulas_returnsBadRequest() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestStatus.IN_EVALUATION);

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

    private List<Map<String, Object>> notificationRows(Long itemId) {
        return jdbcTemplate.queryForList(
                "SELECT destinatario, plantilla, asunto FROM notificacion WHERE clave_idempotencia = ?",
                "room-request-item:" + itemId + ":RESOLVED");
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
                .status(RoomRequestStatus.IN_EVALUATION)
                .decidedBy("subsecretaria@frc.utn.edu.ar")
                .decidedAt(LocalDateTime.now())
                .build();
        item.assignClassrooms(List.of(classroomId), List.of(List.of(occurrenceId)));
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
        if (status != RoomRequestStatus.NEW) {
            item.decide(status, "subsecretaria@frc.utn.edu.ar", null, LocalDateTime.now());
        }
        roomRequestRepository.save(request);
        return item;
    }
}
