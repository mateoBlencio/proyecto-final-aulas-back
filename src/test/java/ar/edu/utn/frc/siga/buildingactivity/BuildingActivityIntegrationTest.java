package ar.edu.utn.frc.siga.buildingactivity;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin {@code @Transactional}: el listener corre async despues del commit, asi que los datos tienen que estar
 * commiteados y la espera es con Awaitility.
 */
@Import(IntegrationTestData.class)
@DisplayName("Movimientos por edificio (integracion)")
class BuildingActivityIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(8, 0);
    private static final int DURATION = 90;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AllocationService allocationService;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Test
    @DisplayName("reasignar como SUBSECRETARIA deja una actividad con su linea, con el jsonb como arreglo de objetos")
    void reallocateLeavesActivityWithLine() throws Exception {
        Scenario s = scenario();

        reallocate(s);

        awaitActivities(s.building().getId(), 1);
        assertThat(jdbcTemplate.queryForObject(
                "select jsonb_typeof(lineas) from actividad_edificio where id_edificio = ?", String.class,
                s.building().getId())).isEqualTo("array");
        assertThat(jdbcTemplate.queryForObject(
                "select jsonb_array_length(lineas) from actividad_edificio where id_edificio = ?", Integer.class,
                s.building().getId())).isEqualTo(1);

        JsonNode activity = firstActivity(s.auxiliary());
        assertThat(activity.at("/building/id").asLong()).isEqualTo(s.building().getId());
        assertThat(activity.at("/source").asText()).isEqualTo("MANUAL");
        JsonNode line = activity.at("/lines/0");
        assertThat(line.at("/occurrenceId").asLong()).isEqualTo(s.occurrence().getId());
        assertThat(line.at("/date").asText()).isEqualTo(s.date().toString());
        assertThat(line.at("/startTime").asText()).startsWith("08:00");
        assertThat(line.at("/endTime").asText()).startsWith("09:30");
        assertThat(line.at("/fromClassroom/id").asLong()).isEqualTo(s.from().getId());
        assertThat(line.at("/toClassroom/id").asLong()).isEqualTo(s.to().getId());
        assertThat(line.at("/toClassroom/roomNumber").asInt()).isEqualTo(s.to().getRoomNumber());
    }

    @Test
    @DisplayName("unseen-count da 1 para el auxiliar del edificio, no cambia para el actor y POST /seen lo baja a 0")
    void unseenCountAndSeen() throws Exception {
        Scenario s = scenario();
        int actorBefore = unseenCount(mockMvc);
        assertThat(unseenCount(s.auxiliary())).isZero();

        reallocate(s);
        awaitActivities(s.building().getId(), 1);

        assertThat(unseenCount(s.auxiliary())).isEqualTo(1);
        assertThat(unseenCount(mockMvc)).isEqualTo(actorBefore);
        assertThat(firstActivity(s.auxiliary()).at("/seen").asBoolean()).isFalse();

        long lastSeenId = firstActivity(s.auxiliary()).at("/id").asLong();
        s.auxiliary().perform(post("/v1/building-activity/seen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastSeenId\":" + lastSeenId + "}"))
                .andExpect(status().isNoContent());

        assertThat(unseenCount(s.auxiliary())).isZero();
        assertThat(firstActivity(s.auxiliary()).at("/seen").asBoolean()).isTrue();

        s.auxiliary().perform(post("/v1/building-activity/seen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastSeenId\":1}"))
                .andExpect(status().isNoContent());
        assertThat(unseenCount(s.auxiliary())).isZero();
    }

    @Test
    @DisplayName("un auxiliar de otro edificio no ve la actividad y recibe 403 si pide ese edificio")
    void auxiliaryOfAnotherBuildingSeesNothing() throws Exception {
        Scenario s = scenario();
        Building otherBuilding = testData.edificio();
        MockMvc otherAuxiliary = auxiliaryOf(otherBuilding);

        reallocate(s);
        awaitActivities(s.building().getId(), 1);

        assertThat(unseenCount(otherAuxiliary)).isZero();
        otherAuxiliary.perform(get("/v1/building-activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        otherAuxiliary.perform(get("/v1/building-activity").param("buildingId", s.building().getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("el mismo evento reentregado no duplica la actividad")
    void redeliveredEventDoesNotDuplicate() throws Exception {
        Scenario s = scenario();
        String actor = "sub-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar";
        mockMvcAs(actor, SystemRole.SUBSECRETARIA);
        ClassroomMovements event = new ClassroomMovements(UUID.randomUUID().toString(), actor, AllocationSource.MANUAL,
                "reentrega", Instant.now(), List.of(new Change(s.occurrence().getId(), s.eventId(), s.date(), START,
                START.plusMinutes(DURATION), s.from().getId(), s.to().getId())));

        publishInTransaction(event);
        awaitActivities(s.building().getId(), 1);
        publishInTransaction(event);

        await().during(Duration.ofSeconds(3)).atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(activitiesIn(s.building().getId())).isEqualTo(1));
    }

    @Test
    @DisplayName("liberar y reasignar la misma ocurrencia en una transaccion deja una sola actividad con la linea fusionada")
    void deallocateAndReallocateInOneTransactionYieldsOneMergedActivity() throws Exception {
        Scenario s = scenario();
        Long occurrenceId = s.occurrence().getId();

        asFixtureUser(() -> transactionTemplate.executeWithoutResult(status -> {
            allocationService.deallocate(new DeallocationCommand(
                    List.of(new AllocationTarget.Occurrences(List.of(occurrenceId))), "libera por obra"));
            allocationService.reallocate(new AllocationCommand(
                    List.of(new AllocationItem(new AllocationTarget.Occurrences(List.of(occurrenceId)),
                            s.to().getId())),
                    "reasigna por obra", AllocationSource.MANUAL));
        }));

        awaitActivities(s.building().getId(), 1);
        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(activitiesIn(s.building().getId())).isEqualTo(1));
        JsonNode activity = firstActivity(s.auxiliary());
        assertThat(activity.at("/lines")).hasSize(1);
        JsonNode line = activity.at("/lines/0");
        assertThat(line.at("/occurrenceId").asLong()).isEqualTo(occurrenceId);
        assertThat(line.at("/fromClassroom/id").asLong()).isEqualTo(s.from().getId());
        assertThat(line.at("/toClassroom/id").asLong()).isEqualTo(s.to().getId());
    }

    @Test
    @DisplayName("reasignar por roomrequest un pedido de un aula ya asignado deja una actividad nueva con aula vieja y nueva")
    void reassigningAssignedRoomRequestItemLeavesOneActivityWithMergedLine() throws Exception {
        Building building = testData.edificio();
        Classroom first = testData.aula(building);
        Classroom second = testData.aula(building);
        MockMvc auxiliary = auxiliaryOf(building);
        RoomRequestItem item = seedFinalExamItem(1);

        assignItem(item, List.of(first.getId()), "");
        awaitActivities(building.getId(), 1);
        assignItem(item, List.of(second.getId()), "");

        awaitActivities(building.getId(), 2);
        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(activitiesIn(building.getId())).isEqualTo(2));
        JsonNode newest = firstActivity(auxiliary);
        assertThat(newest.at("/lines")).hasSize(1);
        assertThat(newest.at("/lines/0/fromClassroom/id").asLong()).isEqualTo(first.getId());
        assertThat(newest.at("/lines/0/toClassroom/id").asLong()).isEqualTo(second.getId());
    }

    @Test
    @DisplayName("reasignar por roomrequest un pedido de dos aulas (deallocate + reallocate en una operacion) deja una sola actividad nueva")
    void reassigningTwoRoomItemLeavesOneNewActivity() throws Exception {
        Building building = testData.edificio();
        Classroom a1 = testData.aula(building);
        Classroom a2 = testData.aula(building);
        Classroom b1 = testData.aula(building);
        Classroom b2 = testData.aula(building);
        MockMvc auxiliary = auxiliaryOf(building);
        RoomRequestItem item = seedFinalExamItem(2);

        assignItem(item, List.of(a1.getId(), a2.getId()), "");
        awaitActivities(building.getId(), 1);
        assignItem(item, List.of(b1.getId(), b2.getId()), "");

        awaitActivities(building.getId(), 2);
        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(activitiesIn(building.getId())).isEqualTo(2));
        JsonNode newest = firstActivity(auxiliary);
        List<String> lines = new java.util.ArrayList<>();
        newest.at("/lines").forEach(l -> lines.add(l.at("/fromClassroom/id").asText("-") + ">"
                + l.at("/toClassroom/id").asText("-")));
        assertThat(lines).contains(a1.getId() + ">" + b1.getId());
        assertThat(lines).anyMatch(l -> l.endsWith(">" + b2.getId()));
        assertThat(lines).anyMatch(l -> l.startsWith(a2.getId() + ">"));
    }

    private RoomRequestItem seedFinalExamItem(int classroomCount) {
        RoomRequest request = RoomRequest.builder()
                .type(RoomRequestType.FINAL_EXAM)
                .scope(AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(testData.materiaYComision().subjectId())
                .build();
        RoomRequestItem item = RoomRequestItem.builder()
                .date(LocalDate.now().plusDays(40))
                .startTime(LocalTime.of(9, 0))
                .duration(Duration.ofMinutes(90))
                .estimated(10)
                .classroomCount(classroomCount)
                .build();
        request.addItem(item);
        roomRequestRepository.save(request);
        return item;
    }

    private void assignItem(RoomRequestItem item, List<Long> classroomIds, String reason) throws Exception {
        mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("classroomIds", classroomIds, "reason", reason))))
                .andExpect(status().isOk());
    }

    private void publishInTransaction(ClassroomMovements event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private Scenario scenario() {
        var sc = testData.materiaYComision();
        Building building = testData.edificio();
        Classroom from = testData.aula(building);
        Classroom to = testData.aula(building);
        LocalDate date = LocalDate.now().plusDays(8);
        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        allocationRepository.save(Allocation.builder()
                .occurrenceId(occurrence.getId())
                .classroomId(from.getId())
                .source(AllocationSource.MANUAL)
                .build());
        return new Scenario(building, from, to, occurrence, eventId, date, auxiliaryOf(building));
    }

    private MockMvc auxiliaryOf(Building building) {
        return mockMvcAsScoped("aux-" + IntegrationTestData.nextSeq() + "@frc.utn.edu.ar",
                SystemRole.AUXILIAR_AULICO, ScopeType.BUILDING, building.getId());
    }

    private void reallocate(Scenario s) throws Exception {
        var body = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(s.occurrence().getId()), null, null, null, s.to().getId())),
                "cambio por obra");
        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private int activitiesIn(Long buildingId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from actividad_edificio where id_edificio = ?", Integer.class, buildingId);
    }

    private void awaitActivities(Long buildingId, int expected) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(activitiesIn(buildingId)).isEqualTo(expected));
    }

    private int unseenCount(MockMvc client) throws Exception {
        String body = client.perform(get("/v1/building-activity/unseen-count"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("count").asInt();
    }

    private JsonNode firstActivity(MockMvc client) throws Exception {
        String body = client.perform(get("/v1/building-activity"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).get("content");
        assertThat(content).isNotEmpty();
        return content.get(0);
    }

    private record Scenario(Building building, Classroom from, Classroom to, Occurrence occurrence, Long eventId,
                            LocalDate date, MockMvc auxiliary) {
    }
}
