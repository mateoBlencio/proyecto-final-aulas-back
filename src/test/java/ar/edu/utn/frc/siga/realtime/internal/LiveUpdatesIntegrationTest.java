package ar.edu.utn.frc.siga.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivityType;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivityRepository;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@Import(IntegrationTestData.class)
@DisplayName("Avisos en tiempo real por SSE (integración)")
class LiveUpdatesIntegrationTest extends AbstractIntegrationTest {

    private static final String ALLOCATIONS = "event:change\ndata:ALLOCATIONS";
    private static final String ROOM_REQUESTS = "event:change\ndata:ROOM_REQUESTS";
    private static final String BUILDING_ACTIVITY = "event:change\ndata:BUILDING_ACTIVITY";

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private LiveDispatcher dispatcher;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private BuildingActivityRepository buildingActivityRepository;
    @Autowired
    private RoomRequestRepository roomRequestRepository;

    private Building buildingA;
    private Building buildingB;
    private List<Occurrence> occurrences;

    @BeforeEach
    void setUp() {
        buildingA = testData.edificio();
        buildingB = testData.edificio();
        SubjectAndCommission academic = testData.materiaYComision();
        LocalDate start = LocalDate.now().plusYears(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 100);
        Long eventId = testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), DayOfWeek.TUESDAY,
                LocalTime.of(10, 0), 90, start, start.plusWeeks(1), 30);
        occurrences = occurrenceRepository.findByEvent_Id(eventId);
        dispatcher.flush();
    }

    @Test
    @DisplayName("Al conectarse recibe ready, y con una asignación nueva recibe el aviso de asignaciones")
    void adminReceivesAllocationChange() throws Exception {
        MockHttpServletResponse stream = connect(mockMvc);
        assertThat(stream.getContentAsString()).contains("event:ready");

        allocate(occurrences.get(0), testData.aula(buildingA));
        dispatcher.flush();

        assertThat(stream.getContentAsString()).contains(ALLOCATIONS);
    }

    @Test
    @DisplayName("Un auxiliar no recibe cambios de asignaciones de otro edificio, y sí los del suyo")
    void auxiliarOnlyReceivesOwnBuildingAllocations() throws Exception {
        MockHttpServletResponse stream = connect(auxiliarOf(buildingA));

        allocate(occurrences.get(0), testData.aula(buildingB));
        dispatcher.flush();
        assertThat(stream.getContentAsString()).doesNotContain(ALLOCATIONS);

        allocate(occurrences.get(1), testData.aula(buildingA));
        dispatcher.flush();
        assertThat(stream.getContentAsString()).contains(ALLOCATIONS);
    }

    @Test
    @DisplayName("Cambiar una asignación de aula avisa también al edificio del aula anterior")
    void movingAllocationNotifiesPreviousBuilding() throws Exception {
        Allocation allocation = allocate(occurrences.get(0), testData.aula(buildingA));
        dispatcher.flush();
        MockHttpServletResponse stream = connect(auxiliarOf(buildingA));

        allocation.setClassroomId(testData.aula(buildingB).getId());
        allocationRepository.save(allocation);
        dispatcher.flush();

        assertThat(stream.getContentAsString()).contains(ALLOCATIONS);
    }

    @Test
    @DisplayName("La actividad de edificio llega solo a quien ve ese edificio")
    void buildingActivityIsScopedByBuilding() throws Exception {
        MockHttpServletResponse stream = connect(auxiliarOf(buildingA));

        registerActivity(buildingB);
        dispatcher.flush();
        assertThat(stream.getContentAsString()).doesNotContain(BUILDING_ACTIVITY);

        registerActivity(buildingA);
        dispatcher.flush();
        assertThat(stream.getContentAsString()).contains(BUILDING_ACTIVITY);
    }

    @Test
    @DisplayName("Un pedido nuevo sin derivar avisa a Subsecretaría y no a un auxiliar")
    void newRoomRequestOnlyReachesUnrestrictedReaders() throws Exception {
        MockHttpServletResponse admin = connect(mockMvc);
        MockHttpServletResponse auxiliar = connect(auxiliarOf(buildingA));

        SubjectAndCommission academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.CONFERENCE, academic.subjectId());
        testData.itemDePedido(request, academic.commissionId(), LocalDate.now().plusMonths(2), RoomRequestStatus.NEW);
        roomRequestRepository.save(request);
        dispatcher.flush();

        assertThat(admin.getContentAsString()).contains(ROOM_REQUESTS);
        assertThat(auxiliar.getContentAsString()).doesNotContain(ROOM_REQUESTS);
    }

    private MockHttpServletResponse connect(MockMvc client) throws Exception {
        return client.perform(get("/v1/live"))
                .andExpect(request().asyncStarted())
                .andReturn()
                .getResponse();
    }

    private MockMvc auxiliarOf(Building building) {
        return mockMvcAsScoped("live-aux-" + UUID.randomUUID() + "@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO,
                ScopeType.BUILDING, building.getId());
    }

    private Allocation allocate(Occurrence occurrence, Classroom classroom) {
        return allocationRepository.save(Allocation.builder().occurrenceId(occurrence.getId())
                .classroomId(classroom.getId()).source(AllocationSource.MANUAL).build());
    }

    private void registerActivity(Building building) {
        buildingActivityRepository.save(BuildingActivity.builder()
                .type(BuildingActivityType.ALLOCATION_CHANGE)
                .buildingId(building.getId())
                .buildingName(building.getName())
                .operationId(UUID.randomUUID().toString())
                .actor("integration-test@frc.utn.edu.ar")
                .source(AllocationSource.MANUAL)
                .occurredAt(Instant.now())
                .lines("[]")
                .build());
    }
}
