package ar.edu.utn.frc.siga.roomrequest.direct;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("POST /v1/events/direct (integración)")
class DirectEventIntegrationTest extends AbstractIntegrationTest {

    private static final String REASON = "Motivo del alta directa";
    private static final String EVENT_BY_CLASSROOM = """
            select e.*, o.id_ocurrencia, o.orden_aula, o.id_ocurrencia_principal, a.observaciones
            from asignacion_aula a
            join ocurrencia o on o.id_ocurrencia = a.id_ocurrencia
            join evento_unico e on e.id_evento_academico = o.id_evento_academico
            where a.id_aula = ?
            """;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Test
    @DisplayName("alta con 2 aulas: un evento, ocurrencias 1 y 2 enlazadas y reason en la observación de las dos asignaciones")
    void create_twoRooms_persistsEventMirrorsAndReasonOnBothAllocations() throws Exception {
        Classroom first = testData.aula(testData.edificio());
        Classroom second = testData.aula(testData.edificio());
        Map<String, Object> draft = draft("FINAL_EXAM", LocalDate.now().plusDays(30));
        draft.put("subjectId", testData.materiaYComision().subjectId());
        draft.put("classroomCount", 2);

        direct(draft, List.of(first.getId(), second.getId())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        Map<String, Object> principal = rowFor(first);
        Map<String, Object> mirror = rowFor(second);
        assertThat(mirror.get("id_evento_academico")).isEqualTo(principal.get("id_evento_academico"));
        assertThat(principal.get("tipo_actividad")).isEqualTo("EXAMEN_FINAL");
        assertThat(principal.get("orden_aula")).isEqualTo(1);
        assertThat(principal.get("id_ocurrencia_principal")).isNull();
        assertThat(mirror.get("orden_aula")).isEqualTo(2);
        assertThat(mirror.get("id_ocurrencia_principal")).isEqualTo(principal.get("id_ocurrencia"));
        assertThat(principal.get("observaciones")).isEqualTo(REASON);
        assertThat(mirror.get("observaciones")).isEqualTo(REASON);
    }

    @Test
    @DisplayName("la misma aula repetida en classroomIds: 400 y no queda ninguna asignación")
    void create_repeatedClassroom_returnsBadRequest() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        Map<String, Object> draft = draft("CONFERENCE", LocalDate.now().plusDays(31));
        draft.put("classroomCount", 2);

        direct(draft, List.of(aula.getId(), aula.getId())).andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from asignacion_aula where id_aula = ?", Integer.class, aula.getId())).isZero();
    }

    @Test
    @DisplayName("aula ocupada en esa fecha y horario: 409 con code ALLOCATION_CONFLICT")
    void create_occupiedClassroom_returnsConflict() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        Map<String, Object> draft = draft("CONFERENCE", LocalDate.now().plusDays(32));
        direct(draft, List.of(aula.getId())).andExpect(status().isCreated());

        direct(draft, List.of(aula.getId())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from asignacion_aula where id_aula = ?", Integer.class, aula.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("suggestionId inexistente: 410")
    void create_unknownSuggestion_returnsGone() throws Exception {
        Classroom aula = testData.aula(testData.edificio());

        mockMvc.perform(post("/v1/events/direct").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(
                                draft("CONFERENCE", LocalDate.now().plusDays(33)), List.of(aula.getId()), "sug_no-existe"))))
                .andExpect(status().isGone());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from asignacion_aula where id_aula = ?", Integer.class, aula.getId())).isZero();
    }

    @Test
    @DisplayName("parcial en horario de clases: el evento queda en otra aula a la hora del cursado y la clase conserva la suya")
    void create_partialInClass_keepsClassAllocation() throws Exception {
        Classroom classRoom = testData.aula(testData.edificio());
        Classroom examRoom = testData.aula(testData.edificio());
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(34);
        Long classEventId = testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), date.getDayOfWeek(),
                LocalTime.of(18, 0), 120, date, date, 30);
        Occurrence classOccurrence = occurrenceRepository.findByEvent_Id(classEventId).getFirst();
        allocate(classOccurrence.getId(), classRoom.getId());
        assertThat(classroomOf(classOccurrence.getId())).isEqualTo(classRoom.getId());

        Map<String, Object> draft = draft("PARTIAL_EXAM_IN_CLASS", date);
        draft.remove("startTime");
        draft.remove("endTime");
        draft.put("subjectId", sc.subjectId());
        draft.put("commissionId", sc.commissionId());
        direct(draft, List.of(examRoom.getId())).andExpect(status().isCreated());

        assertThat(classroomOf(classOccurrence.getId())).isEqualTo(classRoom.getId());
        Map<String, Object> exam = rowFor(examRoom);
        assertThat(exam.get("tipo_actividad")).isEqualTo("PARCIAL");
        assertThat(exam.get("id_evento_academico")).isNotEqualTo(classEventId);
        Map<String, Object> examEvent = jdbcTemplate.queryForMap(
                "select hora_inicio, duracion_minutos from evento_academico where id_evento_academico = ?",
                exam.get("id_evento_academico"));
        assertThat(examEvent.get("hora_inicio")).hasToString("18:00:00");
        assertThat(examEvent.get("duracion_minutos")).isEqualTo(120);
    }

    @Test
    @DisplayName("CONFERENCE: el evento queda con tipo_actividad CONFERENCIA (CHECK de V24)")
    void create_conference_persistsConferenciaKind() throws Exception {
        Classroom aula = testData.aula(testData.edificio());

        direct(draft("CONFERENCE", LocalDate.now().plusDays(35)), List.of(aula.getId())).andExpect(status().isCreated());

        assertThat(rowFor(aula).get("tipo_actividad")).isEqualTo("CONFERENCIA");
    }

    @Test
    @DisplayName("alta directa con requisitos: evento_unico guarda proyector, computadoras, cantidad y software")
    void create_withRequirements_persistsThem() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        testData.recursoDeAula(aula, testData.tipoRecurso("Cantidad de PC", ResourceValueKind.COUNT), 40);
        testData.recursoDeAula(aula, testData.tipoRecurso("Proyector", ResourceValueKind.BOOLEAN), 1);
        Map<String, Object> draft = draft("CONFERENCE", LocalDate.now().plusDays(36));
        draft.put("requiresProjector", true);
        draft.put("requiresComputers", true);
        draft.put("computerCount", 30);
        draft.put("requiredSoftware", "MATLAB");

        direct(draft, List.of(aula.getId())).andExpect(status().isCreated());

        assertRequirements(rowFor(aula));
    }

    @Test
    @DisplayName("solicitud resuelta con requisitos: el evento_unico que crea los guarda igual")
    void assignRoomRequest_withRequirements_persistsThem() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.CONFERENCE, null);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(LocalDate.now().plusDays(37))
                .startTime(LocalTime.of(10, 0))
                .duration(Duration.ofMinutes(120))
                .estimated(30)
                .classroomCount(1)
                .requiresProjector(true)
                .requiresComputers(true)
                .computerCount(30)
                .requiredSoftware("MATLAB")
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);

        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "classroomIds", List.of(aula.getId()), "reason", ""))))
                .andExpect(status().isOk());

        Map<String, Object> row = rowFor(aula);
        assertThat(row.get("tipo_actividad")).isEqualTo("CONFERENCIA");
        assertRequirements(row);
    }

    @Test
    @DisplayName("description de 600 caracteres: se guarda completa (columna ampliada a 1000)")
    void create_longDescription_isStoredInFull() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        String description = "d".repeat(600);
        Map<String, Object> draft = draft("OTHER", LocalDate.now().plusDays(38));
        draft.put("description", description);

        direct(draft, List.of(aula.getId())).andExpect(status().isCreated());

        assertThat(rowFor(aula).get("descripcion")).isEqualTo(description);
    }

    @Test
    @DisplayName("auxiliar con un aula sin computadoras de su edificio para un borrador que las exige: 400 y nada persistido")
    void create_auxiliarRoomWithoutComputers_returnsBadRequest() throws Exception {
        Building building = testData.edificio();
        Classroom plainRoom = testData.aula(building);
        MockMvc auxiliar = auxiliarOf(building);
        Map<String, Object> draft = draft("CONFERENCE", LocalDate.now().plusDays(39));
        draft.put("requiresComputers", true);
        draft.put("computerCount", 30);

        directAs(auxiliar, draft, List.of(plainRoom.getId())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Estas aulas no cumplen")));

        assertNothingPersisted(plainRoom, draft);
    }

    @Test
    @DisplayName("auxiliar con un aula de otro edificio: 400 por no ser candidata y nada persistido")
    void create_auxiliarRoomOfOtherBuilding_isRejected() throws Exception {
        Classroom foreignRoom = testData.aula(testData.edificio());
        testData.recursoDeAula(foreignRoom, testData.tipoRecurso("Cantidad de PC", ResourceValueKind.COUNT), 40);
        MockMvc auxiliar = auxiliarOf(testData.edificio());
        Map<String, Object> draft = draft("CONFERENCE", LocalDate.now().plusDays(40));
        draft.put("requiresComputers", true);
        draft.put("computerCount", 30);

        directAs(auxiliar, draft, List.of(foreignRoom.getId())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Estas aulas no cumplen")));

        assertNothingPersisted(foreignRoom, draft);
    }

    private MockMvc auxiliarOf(Building building) {
        return mockMvcAsScoped("auxiliar-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
    }

    private void assertNothingPersisted(Classroom classroom, Map<String, Object> draft) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from asignacion_aula where id_aula = ?", Integer.class, classroom.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from ocurrencia where fecha = ?", Integer.class,
                java.sql.Date.valueOf((String) draft.get("date")))).isZero();
    }

    private ResultActions directAs(MockMvc client, Map<String, Object> draft, List<Long> classroomIds) throws Exception {
        return client.perform(post("/v1/events/direct").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body(draft, classroomIds, null))));
    }

    private static void assertRequirements(Map<String, Object> row) {
        assertThat(row.get("requiere_proyector")).isEqualTo(true);
        assertThat(row.get("requiere_computadoras")).isEqualTo(true);
        assertThat(row.get("cantidad_computadoras")).isEqualTo(30);
        assertThat(row.get("software_requerido")).isEqualTo("MATLAB");
    }

    private Map<String, Object> rowFor(Classroom classroom) {
        return jdbcTemplate.queryForMap(EVENT_BY_CLASSROOM, classroom.getId());
    }

    private Long classroomOf(Long occurrenceId) {
        return jdbcTemplate.queryForObject(
                "select id_aula from asignacion_aula where id_ocurrencia = ?", Long.class, occurrenceId);
    }

    private void allocate(Long occurrenceId, Long classroomId) throws Exception {
        AllocationBatchRequestDto batch = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)),
                "Ocupación previa para el test");
        mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isCreated());
    }

    private ResultActions direct(Map<String, Object> draft, List<Long> classroomIds) throws Exception {
        return mockMvc.perform(post("/v1/events/direct").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body(draft, classroomIds, null))));
    }

    private static Map<String, Object> body(Map<String, Object> draft, List<Long> classroomIds, String suggestionId) {
        Map<String, Object> body = new HashMap<>();
        body.put("draft", draft);
        body.put("classroomIds", classroomIds);
        body.put("suggestionId", suggestionId);
        body.put("reason", REASON);
        return body;
    }

    private static Map<String, Object> draft(String type, LocalDate date) {
        Map<String, Object> draft = new HashMap<>();
        draft.put("type", type);
        draft.put("date", date.toString());
        draft.put("startTime", "10:00");
        draft.put("endTime", "12:00");
        draft.put("estimated", 30);
        draft.put("description", "Evento de prueba");
        return draft;
    }
}
