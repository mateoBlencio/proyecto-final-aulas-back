package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.notification.internal.render.NotificationRenderer;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El mail de "pedido resuelto" sale después del commit de notify, en una transacción propia. Con el render
 * roto, el endpoint tiene que seguir devolviendo 200 y el ítem tiene que quedar NOTIFIED en la base.
 */
@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/notify con el mail roto (integración)")
class RoomRequestItemNotifyMailFailureIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private NotificationRenderer notificationRenderer;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private RoomRequestItemRepository itemRepository;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("el render lanza: 200, el ítem queda NOTIFIED en la base y no se encola ninguna notificación")
    void notify_renderLanza_elItemQuedaNotificado() throws Exception {
        when(notificationRenderer.render(any(), any())).thenThrow(new IllegalStateException("plantilla rota"));
        RoomRequestItem item = seedPreApprovedItem();

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/notify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"))
                .andExpect(jsonPath("$.notifiedAt").isNotEmpty());

        verify(notificationRenderer).render(any(), any());
        RoomRequestItem persisted = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(RoomRequestStatus.NOTIFIED);
        assertThat(persisted.getNotifiedAt()).isNotNull();
        assertThat(persisted.getNotificationRound()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT 1 FROM notificacion WHERE clave_idempotencia = ?",
                "room-request-item:" + item.getId() + ":NOTIFIED:1")).isEmpty();
    }

    private RoomRequestItem seedPreApprovedItem() {
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
                .classroomCount(1)
                .status(RoomRequestStatus.IN_EVALUATION)
                .decidedBy("subsecretaria@frc.utn.edu.ar")
                .decidedAt(Instant.now())
                .build();
        item.assignClassrooms(List.of(classroomId), List.of(List.of(occurrenceId)), List.of(), Map.of());
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }
}
