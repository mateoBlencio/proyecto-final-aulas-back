package ar.edu.utn.frc.siga.roomrequest;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * El personal ve enabledInSysacad en las aulas de un pedido; el formulario público no: la respuesta de
 * POST /v1/room-requests lo omite (campo nulo con NON_NULL).
 */
@Import(IntegrationTestData.class)
@DisplayName("Solicitudes: enabledInSysacad solo para el personal (integración)")
class RoomRequestSysacadVisibilityIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(18, 0);

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    private MockMvc anonymousMockMvc;
    private final List<Long> disabledClassroomIds = new ArrayList<>();

    @BeforeEach
    void setUpAnonymousMockMvc() {
        anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void restoreFlags() {
        disabledClassroomIds.forEach(id -> {
            Classroom fresh = classroomRepository.findById(id).orElseThrow();
            fresh.setSysacadEnabled(true);
            classroomRepository.save(fresh);
        });
        disabledClassroomIds.clear();
    }

    private Classroom classroomDisabledInSysacad() {
        Classroom classroom = testData.aula(testData.edificio());
        classroom.setSysacadEnabled(false);
        classroom.setSysacadHash("hash-" + classroom.getId());
        disabledClassroomIds.add(classroom.getId());
        return classroomRepository.save(classroom);
    }

    private LocalDate futureMonday() {
        return LocalDate.now().plusDays(30).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 100);
    }

    /** Evento de una sola clase en {@code date}, asignado a {@code classroom}. */
    private Long classAllocatedTo(IntegrationTestData.SubjectAndCommission academic, LocalDate date,
            Classroom classroom) {
        Long eventId = testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), date.getDayOfWeek(),
                START, 90, date, date, 30);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        allocationRepository.save(Allocation.builder().occurrenceId(occurrence.getId())
                .classroomId(classroom.getId()).source(AllocationSource.MANUAL).build());
        return eventId;
    }

    private static Map<String, Object> requester() {
        Map<String, Object> requester = new LinkedHashMap<>();
        requester.put("scope", "GRADO");
        requester.put("teacherName", "Ada Lovelace");
        requester.put("teacherEmail", "ada@frc.utn.edu.ar");
        requester.put("teacherPhone", "351-1234567");
        return requester;
    }

    @Test
    @DisplayName("POST público: preferredClassrooms de un aula con habilitada = N no trae enabledInSysacad")
    void publicPost_omitsEnabledInSysacadInPreferredClassrooms() throws Exception {
        var academic = testData.materiaYComision();
        Classroom disabled = classroomDisabledInSysacad();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("commissionId", academic.commissionId());
        item.put("date", LocalDate.now().plusDays(7).toString());
        item.put("startTime", "10:00:00");
        item.put("endTime", "12:00:00");
        item.put("estimated", 35);
        item.put("classroomCount", 1);
        item.put("requiresProjector", false);
        item.put("requiresComputers", false);
        item.put("preferredClassroomIds", List.of(disabled.getId()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "PARTIAL_EXAM_OFF_SCHEDULE");
        body.put("requester", requester());
        body.put("subjectId", academic.subjectId());
        body.put("items", List.of(item));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].preferredClassrooms[0].id").value(disabled.getId()))
                .andExpect(jsonPath("$.items[0].preferredClassrooms[0].enabledInSysacad").doesNotExist());
    }

    @Test
    @DisplayName("POST público de un cambio puntual: currentClassrooms (aula con habilitada = N) no trae enabledInSysacad")
    void publicPost_omitsEnabledInSysacadInCurrentClassrooms() throws Exception {
        var academic = testData.materiaYComision();
        Classroom disabled = classroomDisabledInSysacad();
        LocalDate date = futureMonday();
        classAllocatedTo(academic, date, disabled);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("date", date.toString());
        item.put("startTime", "18:00");
        item.put("classroomCount", 1);
        item.put("preferredClassroomIds", List.of(disabled.getId()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "ONE_TIME_ROOM_CHANGE");
        body.put("requester", requester());
        body.put("subjectId", academic.subjectId());
        body.put("commissionId", academic.commissionId());
        body.put("items", List.of(item));

        anonymousMockMvc.perform(post("/v1/room-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].currentClassrooms[0].id").value(disabled.getId()))
                .andExpect(jsonPath("$.items[0].currentClassrooms[0].enabledInSysacad").doesNotExist())
                .andExpect(jsonPath("$.items[0].preferredClassrooms[0].enabledInSysacad").doesNotExist());
    }

    @Test
    @DisplayName("GET del detalle como personal: preferredClassrooms y currentClassrooms traen enabledInSysacad (false con habilitada = N, true en el resto)")
    void staffDetail_includesEnabledInSysacad() throws Exception {
        var academic = testData.materiaYComision();
        Classroom disabled = classroomDisabledInSysacad();
        Classroom enabled = testData.aula(testData.edificio());
        LocalDate date = futureMonday();
        Long eventId = classAllocatedTo(academic, date, disabled);
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.ONE_TIME_ROOM_CHANGE)
                .scope(ar.edu.utn.frc.siga.roomrequest.model.AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(academic.subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .commissionId(academic.commissionId())
                .sourceRecurringEventId(eventId)
                .date(date)
                .startTime(START)
                .duration(java.time.Duration.ofMinutes(90))
                .classroomCount(1)
                .build();
        request.addItem(item);
        item.addPreference(disabled.getId());
        item.addPreference(enabled.getId());
        roomRequestRepository.save(request);

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value(RoomRequestStatus.NEW.name()))
                .andExpect(jsonPath("$.item.currentClassrooms[0].id").value(disabled.getId()))
                .andExpect(jsonPath("$.item.currentClassrooms[*].enabledInSysacad", contains(false)))
                .andExpect(jsonPath("$.item.preferredClassrooms[?(@.id == " + disabled.getId() + ")].enabledInSysacad",
                        contains(false)))
                .andExpect(jsonPath("$.item.preferredClassrooms[?(@.id == " + enabled.getId() + ")].enabledInSysacad",
                        contains(true)));
    }
}
