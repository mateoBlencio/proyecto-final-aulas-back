package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

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
@DisplayName("POST /v1/room-requests/items/{id}/assign (integración)")
class RoomRequestItemAssignApiIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(8, 0);

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("FINAL_EXAM sin evento previo: crea el UniqueEvent y queda IN_EVALUATION")
    void assign_finalExam_creaEventoYAsigna() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        RoomRequestItem item = seedCreatedEventItem(RoomRequestType.FINAL_EXAM, LocalDate.now().plusDays(20));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula.getId()), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"));
    }

    @Test
    @DisplayName("ONE_TIME_ROOM_CHANGE: reasigna la ocurrencia existente de la fecha del pedido")
    void assign_oneTimeRoomChange_reasignaOcurrenciaExistente() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(21);
        Occurrence occurrence = seedOccurrence(sc, date);
        RoomRequestItem item = seedExistingEventItem(RoomRequestType.ONE_TIME_ROOM_CHANGE,
                occurrence.getEvent().getId(), sc, date);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula.getId()), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"));
    }

    @Test
    @DisplayName("0 aulas: 400 Invalid room request")
    void assign_ceroAulas_returnsBadRequest() throws Exception {
        RoomRequestItem item = seedCreatedEventItem(RoomRequestType.FINAL_EXAM, LocalDate.now().plusDays(22));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid room request"));
    }

    @Test
    @DisplayName("más aulas que classroomCount: 400")
    void assign_masAulasQueClassroomCount_returnsBadRequest() throws Exception {
        Classroom aula1 = testData.aula(testData.edificio());
        Classroom aula2 = testData.aula(testData.edificio());
        RoomRequestItem item = seedCreatedEventItem(RoomRequestType.FINAL_EXAM, LocalDate.now().plusDays(23));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula1.getId(), aula2.getId()), null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("menos aulas que classroomCount sin motivo: 400; con motivo: 200 IN_EVALUATION")
    void assign_resolucionParcial_exigeMotivo() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        RoomRequestItem item = seedCreatedEventItem(RoomRequestType.FINAL_EXAM, LocalDate.now().plusDays(24), 2);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula.getId()), null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula.getId()), "solo hay una disponible")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"))
                .andExpect(jsonPath("$.decisionReason").value("solo hay una disponible"));
    }

    @Test
    @DisplayName("pedido ya notificado: 400, no se puede reasignar")
    void assign_yaNotificado_returnsBadRequest() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        RoomRequestItem item = seedNotifiedItem(LocalDate.now().plusDays(25));

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula.getId()), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid room request"));
    }

    @Test
    @DisplayName("aula ya ocupada en esa franja: 409")
    void assign_conflicto_returnsConflict() throws Exception {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(26);
        Classroom aula = testData.aula(testData.edificio());
        Occurrence occupied = seedOccurrence(sc, date, LocalTime.of(9, 0));
        allocateDirectly(occupied.getId(), aula.getId());

        RoomRequestItem item = seedCreatedEventItem(RoomRequestType.FINAL_EXAM, date);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula.getId()), null)))
                .andExpect(status().isConflict());
    }

    /** allocation.maxOverlapMinutes (default 40) tolera un solape parcial en asignaciones MANUAL con
     *  observación no vacía; RoomRequestResolutionServiceImpl.assign() siempre manda una, así que hoy
     *  ningún solape parcial de un pedido de aula puede rechazarse por esta vía (ver assign_conflicto_returnsConflict,
     *  que sí queda blocking porque ahí el solape es total). */
    @Test
    @DisplayName("aula ocupada con solape parcial dentro del margen tolerado: 200, el aula queda asignada igual")
    void assign_solapeParcialTolerado_returnsOk() throws Exception {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(27);
        Classroom aula = testData.aula(testData.edificio());
        Occurrence occupied = seedOccurrence(sc, date);
        allocateDirectly(occupied.getId(), aula.getId());

        RoomRequestItem item = seedCreatedEventItem(RoomRequestType.FINAL_EXAM, date);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula.getId()), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"));
    }

    @Test
    @DisplayName("3 aulas: crea 1 evento con 3 ocurrencias enlazadas y 3 filas de asignación")
    void assign_tresAulas_creaOcurrenciasSimultaneas() throws Exception {
        Classroom aula1 = testData.aula(testData.edificio());
        Classroom aula2 = testData.aula(testData.edificio());
        Classroom aula3 = testData.aula(testData.edificio());
        RoomRequestItem item = seedCreatedEventItem(RoomRequestType.FINAL_EXAM, LocalDate.now().plusDays(40), 3);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula1.getId(), aula2.getId(), aula3.getId()), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_EVALUATION"));

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id_ocurrencia, id_aula, orden from solicitud_item_asignacion where id_item = ?",
                item.getId());
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("orden")).containsExactlyInAnyOrder(1, 2, 3);

        List<Long> occurrenceIds = rows.stream().map(r -> ((Number) r.get("id_ocurrencia")).longValue()).toList();
        List<Occurrence> occurrences = occurrenceRepository.findAllById(occurrenceIds);
        assertThat(occurrences).extracting(Occurrence::getRoomSlot).containsExactlyInAnyOrder(1, 2, 3);
        Long principalId = occurrences.stream().filter(o -> o.getRoomSlot() == 1).findFirst().orElseThrow().getId();
        assertThat(occurrences.stream().filter(o -> o.getRoomSlot() != 1).map(Occurrence::getMirrorOfOccurrenceId))
                .containsExactly(principalId, principalId);
    }

    @Test
    @DisplayName("conflicto en una de tres aulas: no queda ninguna asignada")
    void assign_conflictoConTresAulas_noAplicaNada() throws Exception {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(41);
        Classroom aula1 = testData.aula(testData.edificio());
        Classroom aula2 = testData.aula(testData.edificio());
        Classroom aulaOcupada = testData.aula(testData.edificio());
        Occurrence occupied = seedOccurrence(sc, date, LocalTime.of(9, 0));
        allocateDirectly(occupied.getId(), aulaOcupada.getId());

        RoomRequestItem item = seedCreatedEventItem(RoomRequestType.FINAL_EXAM, date, 3);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula1.getId(), aula2.getId(), aulaOcupada.getId()), null)))
                .andExpect(status().isConflict());

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id_ocurrencia from solicitud_item_asignacion where id_item = ?", item.getId());
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("reasignar 3 aulas por otras 3: libera las ocurrencias mirror viejas")
    void assign_reasignarTresAulas_liberaMirrorsViejos() throws Exception {
        Classroom aula1 = testData.aula(testData.edificio());
        Classroom aula2 = testData.aula(testData.edificio());
        Classroom aula3 = testData.aula(testData.edificio());
        Classroom nuevaAula2 = testData.aula(testData.edificio());
        Classroom nuevaAula3 = testData.aula(testData.edificio());
        RoomRequestItem item = seedCreatedEventItem(RoomRequestType.FINAL_EXAM, LocalDate.now().plusDays(42), 3);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula1.getId(), aula2.getId(), aula3.getId()), null)))
                .andExpect(status().isOk());

        List<Long> oldMirrorOccurrenceIds = jdbcTemplate.queryForList(
                        "select id_ocurrencia from solicitud_item_asignacion where id_item = ? and orden > 1",
                        Long.class, item.getId());

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(aula1.getId(), nuevaAula2.getId(), nuevaAula3.getId()), null)))
                .andExpect(status().isOk());

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id_ocurrencia, id_aula from solicitud_item_asignacion where id_item = ?", item.getId());
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("id_aula"))
                .containsExactlyInAnyOrder(aula1.getId(), nuevaAula2.getId(), nuevaAula3.getId());

        String idsCsv = oldMirrorOccurrenceIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        var releasedAllocations = jdbcTemplate.queryForList(
                "select id_asignacion from asignacion_aula where id_ocurrencia in (" + idsCsv + ")");
        assertThat(releasedAllocations).isEmpty();
    }

    @Test
    @DisplayName("id inexistente: 404")
    void assign_idInexistente_returnsNotFound() throws Exception {
        long unknownId = 999_999_000L + IntegrationTestData.nextSeq();

        mockMvc.perform(post("/v1/room-requests/items/" + unknownId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(1L), null)))
                .andExpect(status().isNotFound());
    }

    private void allocateDirectly(Long occurrenceId, Long classroomId) throws Exception {
        var body = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)),
                "Ocupación previa para el test");
        mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private Occurrence seedOccurrence(IntegrationTestData.SubjectAndCommission sc, LocalDate date) {
        return seedOccurrence(sc, date, START);
    }

    /** startTime igual al del item creado por seedCreatedEventItem (09:00-10:30): solape total, supera
     *  cualquier margen tolerado de allocation.maxOverlapMinutes, no solo uno parcial que hoy se tolera. */
    private Occurrence seedOccurrence(IntegrationTestData.SubjectAndCommission sc, LocalDate date, LocalTime startTime) {
        var dto = new CreateRecurringEventRequestDto(30, startTime, 90, date.getDayOfWeek(), date, date,
                sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();
        return occurrenceRepository.findByEvent_Id(eventId).getFirst();
    }

    private RoomRequestItem seedCreatedEventItem(RoomRequestType type, LocalDate date) {
        return seedCreatedEventItem(type, date, 1);
    }

    private RoomRequestItem seedCreatedEventItem(RoomRequestType type, LocalDate date, int classroomCount) {
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
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(classroomCount)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }

    private RoomRequestItem seedExistingEventItem(RoomRequestType type, Long sourceRecurringEventId,
            IntegrationTestData.SubjectAndCommission sc, LocalDate date) {
        RoomRequest request = RoomRequest.builder()
                .type(type)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(sc.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(sc.commissionId())
                .sourceRecurringEventId(sourceRecurringEventId)
                .date(date)
                .startTime(START)
                .duration(Duration.ofMinutes(90))
                .classroomCount(1)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }

    private RoomRequestItem seedNotifiedItem(LocalDate date) {
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
                .date(date)
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(30)
                .classroomCount(1)
                .status(RoomRequestStatus.IN_EVALUATION)
                .decidedBy("subsecretaria@frc.utn.edu.ar")
                .decidedAt(LocalDateTime.now())
                .notifiedAt(LocalDateTime.now())
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }

    private String assignBody(List<Long> classroomIds, String reason) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "classroomIds", classroomIds,
                "reason", reason == null ? "" : reason));
    }
}
