package ar.edu.utn.frc.siga.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * S7 de la rama de bajas: SysAcad solo informa. Un aula con habilitada = N o de un edificio ausente se
 * ofrece en asignación manual, preview, sugerencias y solicitudes; la baja manual en SIGA sigue
 * dejándola fuera. Cada test usa una fecha propia y bloquea el resto de las aulas de la base para que
 * el aula del caso sea la única candidata.
 */
@Import(IntegrationTestData.class)
@DisplayName("S7: aulas inhabilitadas o de edificios ausentes en SysAcad siguen ofreciéndose (integración)")
class SysacadInformativeClassroomIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime PREVIEW_START = LocalTime.of(11, 0);
    private static final int PREVIEW_MINUTES = 60;
    private static final LocalTime ITEM_START = LocalTime.of(10, 0);
    private static final int ITEM_MINUTES = 120;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private AcademicEventRepository eventRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private ClassroomService classroomService;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<Long> touchedClassroomIds = new ArrayList<>();
    private final List<Long> touchedBuildingIds = new ArrayList<>();

    @AfterEach
    void restoreSysacadFlags() {
        touchedClassroomIds.forEach(id -> {
            Classroom fresh = classroomRepository.findById(id).orElseThrow();
            fresh.setSysacadEnabled(true);
            fresh.setSysacadCurrent(true);
            classroomRepository.save(fresh);
        });
        touchedBuildingIds.forEach(id -> {
            Building fresh = buildingRepository.findById(id).orElseThrow();
            fresh.setSysacadEnabled(true);
            buildingRepository.save(fresh);
        });
        touchedClassroomIds.clear();
        touchedBuildingIds.clear();
    }

    private Classroom classroomDisabledInSysacad(Building building) {
        Classroom classroom = testData.aula(building);
        classroom.setSysacadEnabled(false);
        classroom.setSysacadHash("hash-" + classroom.getId());
        touchedClassroomIds.add(classroom.getId());
        return classroomRepository.save(classroom);
    }

    private Building buildingAbsentFromSysacad() {
        Building building = testData.edificio();
        building.setSysacadEnabled(false);
        building.setSysacadHash("hash-" + building.getId());
        touchedBuildingIds.add(building.getId());
        return buildingRepository.save(building);
    }

    private Classroom deactivatedByHand(Classroom classroom) {
        classroom.deactivate();
        return classroomRepository.save(classroom);
    }

    private Occurrence newOccurrence(LocalDate date, LocalTime start, int minutes) {
        var sc = testData.materiaYComision();
        Long eventId = academicEventService.createRecurringEvent(new CreateRecurringEventRequestDto(
                30, start, minutes, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId())).id();
        return occurrenceRepository.findByEvent_Id(eventId).getFirst();
    }

    private ResultActions postAllocation(Long occurrenceId, Long classroomId) throws Exception {
        var dto = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)), null);
        return mockMvc.perform(post("/v1/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)));
    }

    private void blockAllAvailableRooms(LocalDate date, LocalTime start, int minutes) {
        for (ClassroomResponseDto room : asFixtureUser(classroomService::findAllAvailable)) {
            UniqueEvent blocker = eventRepository.save(UniqueEvent.builder()
                    .enrolled(1).startTime(start).duration(Duration.ofMinutes(minutes))
                    .date(date).description("blocker").kind(UniqueEventKind.OTRO).build());
            Occurrence occurrence = occurrenceRepository.save(Occurrence.builder()
                    .event(blocker).date(date).status(OccurrenceStatus.NEEDS_ROOM).build());
            allocationRepository.save(Allocation.builder()
                    .occurrenceId(occurrence.getId()).classroomId(room.id()).source(AllocationSource.MANUAL).build());
        }
    }

    private PreviewResponseDto preview(Long eventId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PreviewRequestDto(List.of(eventId), null, null, 2))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PreviewResponseDto.class);
    }

    private RoomRequestItem seedItem(LocalDate date) {
        var academic = testData.materiaYComision();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.FINAL_EXAM, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), date, RoomRequestStatus.NEW);
        roomRequestRepository.save(request);
        return item;
    }

    @Test
    @DisplayName("asignación manual a un aula con habilitada = N: 201 y la fila queda guardada")
    void manualAllocation_toClassroomDisabledInSysacad_isCreated() throws Exception {
        Classroom classroom = classroomDisabledInSysacad(testData.edificio());
        Occurrence occurrence = newOccurrence(LocalDate.now().plusDays(220), PREVIEW_START, PREVIEW_MINUTES);

        postAllocation(occurrence.getId(), classroom.getId()).andExpect(status().isCreated());

        assertThat(allocationRepository.findByOccurrenceIdIn(List.of(occurrence.getId())))
                .extracting(Allocation::getClassroomId).containsExactly(classroom.getId());
    }

    @Test
    @DisplayName("asignación manual a un aula de un edificio ausente de SysAcad: 201")
    void manualAllocation_toClassroomOfBuildingAbsentFromSysacad_isCreated() throws Exception {
        Classroom classroom = testData.aula(buildingAbsentFromSysacad());
        Occurrence occurrence = newOccurrence(LocalDate.now().plusDays(221), PREVIEW_START, PREVIEW_MINUTES);

        postAllocation(occurrence.getId(), classroom.getId()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("control: asignación manual a un aula dada de baja en SIGA (aunque SysAcad la habilite): 409 y sin fila")
    void manualAllocation_toClassroomDeactivatedByHand_isConflict() throws Exception {
        Classroom classroom = deactivatedByHand(classroomDisabledInSysacad(testData.edificio()));
        Occurrence occurrence = newOccurrence(LocalDate.now().plusDays(222), PREVIEW_START, PREVIEW_MINUTES);

        postAllocation(occurrence.getId(), classroom.getId()).andExpect(status().isConflict());

        assertThat(allocationRepository.findByOccurrenceIdIn(List.of(occurrence.getId()))).isEmpty();
    }

    @Test
    @DisplayName("control: asignación manual a un aula de un edificio dado de baja en SIGA y ausente de SysAcad: 409")
    void manualAllocation_toClassroomOfBuildingDeactivatedByHand_isConflict() throws Exception {
        Building building = buildingAbsentFromSysacad();
        Classroom classroom = testData.aula(building);
        Building fresh = buildingRepository.findById(building.getId()).orElseThrow();
        fresh.deactivate();
        buildingRepository.save(fresh);
        Occurrence occurrence = newOccurrence(LocalDate.now().plusDays(223), PREVIEW_START, PREVIEW_MINUTES);

        postAllocation(occurrence.getId(), classroom.getId()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("preview: con todo ocupado, el aula con habilitada = N es la propuesta y lleva enabledInSysacad false")
    void preview_proposesClassroomDisabledInSysacad() throws Exception {
        LocalDate date = LocalDate.now().plusDays(224);
        blockAllAvailableRooms(date, PREVIEW_START, PREVIEW_MINUTES);
        Classroom classroom = classroomDisabledInSysacad(testData.edificio());
        Long eventId = newOccurrence(date, PREVIEW_START, PREVIEW_MINUTES).getEvent().getId();

        PreviewResponseDto preview = preview(eventId);

        assertThat(preview.unresolved()).isEmpty();
        assertThat(preview.allocations()).hasSize(1);
        assertThat(preview.allocations().getFirst().classroom().id()).isEqualTo(classroom.getId());
        assertThat(preview.allocations().getFirst().classroom().enabledInSysacad()).isFalse();
    }

    @Test
    @DisplayName("preview: un aula de un edificio ausente de SysAcad también es candidata")
    void preview_proposesClassroomOfBuildingAbsentFromSysacad() throws Exception {
        LocalDate date = LocalDate.now().plusDays(225);
        blockAllAvailableRooms(date, PREVIEW_START, PREVIEW_MINUTES);
        Classroom classroom = testData.aula(buildingAbsentFromSysacad());
        Long eventId = newOccurrence(date, PREVIEW_START, PREVIEW_MINUTES).getEvent().getId();

        PreviewResponseDto preview = preview(eventId);

        assertThat(preview.allocations()).hasSize(1);
        assertThat(preview.allocations().getFirst().classroom().id()).isEqualTo(classroom.getId());
    }

    @Test
    @DisplayName("control: preview con la única aula libre dada de baja en SIGA deja el evento sin resolver")
    void preview_doesNotProposeClassroomDeactivatedByHand() throws Exception {
        LocalDate date = LocalDate.now().plusDays(226);
        blockAllAvailableRooms(date, PREVIEW_START, PREVIEW_MINUTES);
        deactivatedByHand(classroomDisabledInSysacad(testData.edificio()));
        Long eventId = newOccurrence(date, PREVIEW_START, PREVIEW_MINUTES).getEvent().getId();

        PreviewResponseDto preview = preview(eventId);

        assertThat(preview.allocations()).isEmpty();
        assertThat(preview.unresolved()).hasSize(1);
    }

    @Test
    @DisplayName("sugerencia de solicitud: con el resto ocupado, sugiere el aula con habilitada = N y marca enabledInSysacad false")
    void roomRequestSuggestion_offersClassroomDisabledInSysacad() throws Exception {
        LocalDate date = LocalDate.now().plusDays(380);
        blockAllAvailableRooms(date, ITEM_START, ITEM_MINUTES);
        Classroom classroom = classroomDisabledInSysacad(testData.edificio());
        RoomRequestItem item = seedItem(date);

        String body = mockMvc.perform(post("/v1/room-requests/items/" + item.getId() + "/suggestion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUGGESTED"))
                .andReturn().getResponse().getContentAsString();

        Integer suggestedId = JsonPath.read(body, "$.classrooms[0].id");
        assertThat(suggestedId).isEqualTo(classroom.getId().intValue());
        assertThat((Boolean) JsonPath.read(body, "$.classrooms[0].enabledInSysacad")).isFalse();
    }

    @Test
    @DisplayName("aulas candidatas de un pedido: el aula con habilitada = N figura disponible con enabledInSysacad false; la dada de baja en SIGA no figura")
    void allowedClassrooms_listsDisabledClassroomWithFlag_andOmitsDeactivatedByHand() throws Exception {
        Building building = testData.edificio();
        Classroom disabled = classroomDisabledInSysacad(building);
        Classroom enabled = testData.aula(building);
        Classroom deactivated = deactivatedByHand(classroomDisabledInSysacad(building));
        RoomRequestItem item = seedItem(LocalDate.now().plusDays(381));

        mockMvc.perform(get("/v1/room-requests/items/" + item.getId() + "/allowed-classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + disabled.getId() + ")].available", contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + disabled.getId() + ")].enabledInSysacad", contains(false)))
                .andExpect(jsonPath("$[?(@.id == " + enabled.getId() + ")].enabledInSysacad", contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + deactivated.getId() + ")]").isEmpty());
    }
}
