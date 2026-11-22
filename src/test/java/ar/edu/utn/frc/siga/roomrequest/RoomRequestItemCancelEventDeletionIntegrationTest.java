package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los tipos que crean su propio evento único al asignarse (FINAL_EXAM, CONFERENCE, OTHER,
 * PARTIAL_EXAM_OFF_SCHEDULE) lo borran al cancelar el pedido. Los tipos con aula original
 * (ONE_TIME_ROOM_CHANGE, REGULAR_ROOM_CHANGE) trabajan sobre un evento que ya existía y no lo tocan.
 */
@Import(IntegrationTestData.class)
@DisplayName("POST /v1/room-requests/items/{id}/cancel: baja del evento único creado por el pedido (integración)")
class RoomRequestItemCancelEventDeletionIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(9, 0);

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(value = RoomRequestType.class,
            names = {"FINAL_EXAM", "CONFERENCE", "OTHER", "PARTIAL_EXAM_OFF_SCHEDULE"})
    @DisplayName("cancelar un pedido asignado borra el evento, su evento único, sus ocurrencias y las filas de asignación del pedido")
    void cancel_asignado_borraElEventoUnico(RoomRequestType type) throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        RoomRequestItem item = seedItem(type, LocalDate.now().plusDays(20), 1);
        assign(item, aula);
        Long eventId = principalEventId(item);
        List<Long> occurrenceIds = occurrenceIdsOf(eventId);
        assertThat(occurrenceIds).hasSize(1);
        assertThat(rowsOfEvent(eventId)).isEqualTo(new Rows(1, 1, 1, 1, 1));

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(rowsOfEvent(eventId)).isEqualTo(new Rows(0, 0, 0, 0, 0));
        assertThat(allocationsOf(occurrenceIds)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from solicitud_aula_item where id_item = ?",
                Integer.class, item.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("con espejos (2 aulas) borra todas las ocurrencias, incluido el espejo retirado por una reasignación previa")
    void cancel_conEspejosYEspejoRetirado_borraTodasLasOcurrencias() throws Exception {
        Classroom aulaA = testData.aula(testData.edificio());
        Classroom aulaB = testData.aula(testData.edificio());
        Classroom aulaC = testData.aula(testData.edificio());
        RoomRequestItem item = seedItem(RoomRequestType.FINAL_EXAM, LocalDate.now().plusDays(21), 2);
        assign(item, aulaA, aulaB);
        Long eventId = principalEventId(item);
        List<Long> mirrorBeforeReassign = jdbc.queryForList(
                "select id_ocurrencia from solicitud_item_asignacion where id_item = ? and orden > 1",
                Long.class, item.getId());
        assertThat(mirrorBeforeReassign).hasSize(1);

        assign(item, aulaA, aulaC);

        List<Long> occurrenceIds = occurrenceIdsOf(eventId);
        assertThat(occurrenceIds).as("principal, espejo retirado y espejo nuevo")
                .hasSize(3).containsAll(mirrorBeforeReassign);
        assertThat(jdbc.queryForObject("select estado from ocurrencia where id_ocurrencia = ?",
                String.class, mirrorBeforeReassign.getFirst())).isEqualTo("ROOM_RELEASED");

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(rowsOfEvent(eventId)).isEqualTo(new Rows(0, 0, 0, 0, 0));
        assertThat(occurrenceIdsOf(eventId)).isEmpty();
        assertThat(allocationsOf(occurrenceIds)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from solicitud_item_asignacion where id_item = ?",
                Integer.class, item.getId())).isZero();
    }

    @Test
    @DisplayName("FINAL_EXAM sin asignaciones: cancela con 200 y no borra ningún evento")
    void cancel_sinAsignaciones_noBorraNada() throws Exception {
        RoomRequestItem item = seedItem(RoomRequestType.FINAL_EXAM, LocalDate.now().plusDays(22), 1);
        int uniqueEventsBefore = count("select count(*) from evento_unico");
        int eventsBefore = count("select count(*) from evento_academico");
        int occurrencesBefore = count("select count(*) from ocurrencia");

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(count("select count(*) from evento_unico")).isEqualTo(uniqueEventsBefore);
        assertThat(count("select count(*) from evento_academico")).isEqualTo(eventsBefore);
        assertThat(count("select count(*) from ocurrencia")).isEqualTo(occurrencesBefore);
    }

    @Test
    @DisplayName("ONE_TIME_ROOM_CHANGE: cancelar no borra el evento recurrente ni su ocurrencia")
    void cancel_oneTimeRoomChange_noBorraElEvento() throws Exception {
        Classroom aulaB = testData.aula(testData.edificio());
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(23);
        Long eventId = recurringEvent(academic, date, date);
        RoomRequestItem item = seedExistingEventItem(RoomRequestType.ONE_TIME_ROOM_CHANGE, eventId, academic, date, null);
        assign(item, aulaB);

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(count("select count(*) from evento_academico where id_evento_academico = " + eventId)).isEqualTo(1);
        assertThat(occurrenceIdsOf(eventId)).hasSize(1);
    }

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE: cancelar no borra el evento recurrente ni sus ocurrencias")
    void cancel_regularRoomChange_noBorraElEvento() throws Exception {
        Classroom aulaB = testData.aula(testData.edificio());
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        LocalDate first = LocalDate.now().plusDays(24);
        Long eventId = recurringEvent(academic, first, first.plusWeeks(2));
        RoomRequestItem item = seedExistingEventItem(RoomRequestType.REGULAR_ROOM_CHANGE, eventId, academic, null,
                first.getDayOfWeek());
        assign(item, aulaB);

        cancel(item).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(count("select count(*) from evento_academico where id_evento_academico = " + eventId)).isEqualTo(1);
        assertThat(occurrenceIdsOf(eventId)).hasSize(3);
    }

    private record Rows(int events, int uniqueEvents, int occurrences, int itemAllocations, int principalOccurrences) {
    }

    private Rows rowsOfEvent(Long eventId) {
        return new Rows(
                count("select count(*) from evento_academico where id_evento_academico = " + eventId),
                count("select count(*) from evento_unico where id_evento_academico = " + eventId),
                count("select count(*) from ocurrencia where id_evento_academico = " + eventId),
                count("select count(*) from solicitud_item_asignacion a join ocurrencia o "
                        + "on o.id_ocurrencia = a.id_ocurrencia where o.id_evento_academico = " + eventId),
                count("select count(*) from ocurrencia where id_evento_academico = " + eventId
                        + " and id_ocurrencia_principal is null"));
    }

    private int allocationsOf(List<Long> occurrenceIds) {
        String ids = String.join(",", occurrenceIds.stream().map(String::valueOf).toList());
        return count("select count(*) from asignacion_aula where id_ocurrencia in (" + ids + ")");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private Long principalEventId(RoomRequestItem item) {
        return jdbc.queryForObject("select o.id_evento_academico from solicitud_item_asignacion a "
                + "join ocurrencia o on o.id_ocurrencia = a.id_ocurrencia where a.id_item = ? and a.orden = 1",
                Long.class, item.getId());
    }

    private List<Long> occurrenceIdsOf(Long eventId) {
        return occurrenceRepository.findByEvent_Id(eventId).stream().map(o -> o.getId()).toList();
    }

    private void assign(RoomRequestItem item, Classroom... classrooms) throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(classrooms).map(c -> String.valueOf(c.getId())).toList());
        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classroomIds\":[" + ids + "],\"reason\":\"motivo de prueba\"}"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions cancel(RoomRequestItem item) throws Exception {
        return mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"se suspendió\"}"));
    }

    private Long recurringEvent(IntegrationTestData.SubjectAndCommission academic, LocalDate start, LocalDate end) {
        var dto = new CreateRecurringEventRequestDto(30, START, 90, start.getDayOfWeek(), start, end,
                academic.subjectId(), academic.commissionId());
        return academicEventService.createRecurringEvent(dto).id();
    }

    private RoomRequestItem seedItem(RoomRequestType type, LocalDate date, int classroomCount) {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = RoomRequest.builder()
                .type(type)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date)
                .startTime(START)
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(classroomCount)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }

    private RoomRequestItem seedExistingEventItem(RoomRequestType type, Long sourceRecurringEventId,
            IntegrationTestData.SubjectAndCommission academic, LocalDate date, java.time.DayOfWeek dayOfWeek) {
        RoomRequest request = RoomRequest.builder()
                .type(type)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(academic.commissionId())
                .sourceRecurringEventId(sourceRecurringEventId)
                .date(date)
                .dayOfWeek(dayOfWeek)
                .startTime(START)
                .duration(Duration.ofMinutes(90))
                .classroomCount(1)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }
}
