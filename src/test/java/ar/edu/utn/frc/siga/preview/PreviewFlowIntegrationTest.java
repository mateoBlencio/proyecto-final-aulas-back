package ar.edu.utn.frc.siga.preview;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.service.AllocationConflictService;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewItemDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Preview Flow (integración, solver real)")
class PreviewFlowIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(11, 0);
    private static final int DURATION = 60;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private AcademicEventRepository eventRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private OccurrenceService occurrenceService;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private ClassroomService classroomService;
    @Autowired
    private AllocationConflictService allocationConflictService;
    @Autowired
    private ObjectMapper objectMapper;

    private Long createEvent(IntegrationTestData.SubjectAndCommission sc, LocalDate date) {
        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        return academicEventService.createRecurringEvent(dto).id();
    }

    private Occurrence occurrenceOf(Long eventId) {
        return occurrenceRepository.findByEvent_Id(eventId).getFirst();
    }

    private void allocateOk(Long occurrenceId, Long classroomId) throws Exception {
        var dto = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)), null);
        mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    private void allocateDirect(Occurrence occurrence, Long classroomId) {
        allocationRepository.save(Allocation.builder()
                .occurrenceId(occurrence.getId()).classroomId(classroomId).source(AllocationSource.MANUAL).build());
    }

    private void blockAllAvailableRooms(LocalDate date) {
        for (ClassroomResponseDto room : asFixtureUser(classroomService::findAllAvailable)) {
            UniqueEvent blocker = eventRepository.save(UniqueEvent.builder()
                    .enrolled(1).startTime(START).duration(Duration.ofMinutes(DURATION))
                    .date(date).description("blocker").kind(UniqueEventKind.OTRO).build());
            Occurrence occ = occurrenceRepository.save(Occurrence.builder()
                    .event(blocker).date(date).status(OccurrenceStatus.NEEDS_ROOM).build());
            allocationRepository.save(Allocation.builder()
                    .occurrenceId(occ.getId()).classroomId(room.id()).source(AllocationSource.MANUAL).build());
        }
    }

    private PreviewResponseDto autoPreview(List<Long> eventIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PreviewRequestDto(eventIds, null, null, 2))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PreviewResponseDto.class);
    }

    @Test
    @DisplayName("POST /v1/previews resuelve un evento sin aula con una propuesta factible")
    void autoPreview_resolvesEventWithFeasibleRoom() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(100);
        Long eventId = createEvent(sc, date);

        PreviewResponseDto preview = autoPreview(List.of(eventId));

        assertThat(preview.previewId()).isNotBlank();
        assertThat(preview.allocations()).hasSize(1);
        assertThat(preview.allocations().getFirst().event().id()).isEqualTo(eventId);
        assertThat(preview.allocations().getFirst().classroom()).isNotNull();
        assertThat(preview.allocations().getFirst().unchanged()).isFalse();
        assertThat(preview.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("POST /v1/previews con todas las aulas ocupadas: el evento (nuevo, sin aula previa) queda en unresolved")
    void autoPreview_allRoomsOccupied_leavesEventUnresolved() throws Exception {
        var sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(101);
        blockAllAvailableRooms(date);
        Long eventId = createEvent(sc, date);

        PreviewResponseDto preview = autoPreview(List.of(eventId));

        assertThat(preview.allocations()).isEmpty();
        assertThat(preview.unresolved()).hasSize(1);
        assertThat(preview.unresolved().getFirst().event().id()).isEqualTo(eventId);
        assertThat(preview.unresolved().getFirst().conflicts()).isNotEmpty();
        assertThat(preview.unresolved().getFirst().conflicts())
                .allSatisfy(c -> assertThat(c.origin()).isEqualTo(MoveConflictDto.ConflictOrigin.DATABASE));
    }

    @Test
    @DisplayName("POST /v1/previews con re-resolución: evento ya asignado cambia de aula si la actual quedó tomada por un ajeno; el aula pinned ajena no se pisa")
    void autoPreview_reResolvesAllocatedEventAwayFromPinnedForeignRoom() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaActual = testData.aula(edificio);
        testData.aula(edificio);
        LocalDate date = LocalDate.now().plusDays(102);

        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);
        allocateOk(occurrence.getId(), aulaActual.getId());

        var scForeign = testData.materiaYComision();
        Long foreignEventId = createEvent(scForeign, date);
        allocateDirect(occurrenceOf(foreignEventId), aulaActual.getId());

        PreviewResponseDto preview = autoPreview(List.of(eventId));

        assertThat(preview.allocations()).hasSize(1);
        PreviewItemDto proposal = preview.allocations().getFirst();
        assertThat(proposal.event().id()).isEqualTo(eventId);
        assertThat(proposal.classroom()).isNotNull();
        assertThat(proposal.classroom().id()).isNotEqualTo(aulaActual.getId());
        assertThat(proposal.unchanged()).isFalse();
    }

    @Test
    @DisplayName("POST /v1/previews: evento ya asignado a la única aula factible conserva esa aula; unchanged=true")
    void autoPreview_keepsSameRoom_marksUnchangedTrue() throws Exception {
        LocalDate date = LocalDate.now().plusDays(104);
        blockAllAvailableRooms(date);
        Classroom unicaAula = testData.aula(testData.edificio());
        var sc = testData.materiaYComision();
        Long eventId = createEvent(sc, date);
        allocateOk(occurrenceOf(eventId).getId(), unicaAula.getId());

        PreviewResponseDto preview = autoPreview(List.of(eventId));

        assertThat(preview.allocations()).hasSize(1);
        PreviewItemDto proposal = preview.allocations().getFirst();
        assertThat(proposal.classroom().id()).isEqualTo(unicaAula.getId());
        assertThat(proposal.unchanged()).isTrue();
    }

    @Test
    @DisplayName("GET /v1/previews/{id} recupera la propuesta guardada; id inexistente responde 410")
    void getPreview_returnsSavedProposal_andUnknownIdReturns410() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(103);
        Long eventId = createEvent(sc, date);

        PreviewResponseDto original = autoPreview(List.of(eventId));

        MvcResult result = mockMvc.perform(get("/v1/previews/{id}", original.previewId()))
                .andExpect(status().isOk())
                .andReturn();
        PreviewResponseDto recovered = objectMapper.readValue(
                result.getResponse().getContentAsString(), PreviewResponseDto.class);
        assertThat(recovered.previewId()).isEqualTo(original.previewId());
        assertThat(recovered.allocations()).hasSize(1);
        assertThat(recovered.allocations().getFirst().event().id()).isEqualTo(eventId);

        mockMvc.perform(get("/v1/previews/{id}", "prev_no_existe"))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("POST /v1/previews: evento sin ocurrencias pendientes responde 409")
    void autoPreview_noPendingOccurrences_return409() throws Exception {
        LocalDate pastDate = LocalDate.now().minusMonths(2);
        var pastSc = testData.materiaYComision();
        RecurringEvent pastEvent = eventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(START).duration(Duration.ofMinutes(DURATION))
                .dayOfWeek(pastDate.getDayOfWeek()).startDate(pastDate).endDate(pastDate)
                .subjectId(pastSc.subjectId()).commissionId(pastSc.commissionId()).build());
        occurrenceRepository.save(Occurrence.builder()
                .event(pastEvent).date(pastDate).status(OccurrenceStatus.NEEDS_ROOM).build());

        mockMvc.perform(post("/v1/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PreviewRequestDto(List.of(pastEvent.getId()), null, null, 2))))
                .andExpect(status().isConflict());

    }

    @Test
    @DisplayName("confirm: persiste con source AUTOMATIC sin duplicar la allocation existente; re-confirm responde 410")
    void confirm_persistsWithoutDuplicating_andReConfirmReturns410() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        testData.aula(testData.edificio());
        Classroom aulaOriginal = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(106);

        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);
        allocateOk(occurrence.getId(), aulaOriginal.getId());

        PreviewResponseDto preview = autoPreview(List.of(eventId));
        assertThat(preview.allocations()).hasSize(1);
        Long proposedRoom = preview.allocations().getFirst().classroom().id();

        MvcResult confirmResult = mockMvc.perform(post("/v1/previews/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPreviewRequestDto(
                                List.of(new PreviewAllocationDto(eventId, null, proposedRoom))))))
                .andExpect(status().isOk())
                .andReturn();
        ConfirmPreviewResponseDto confirmResponse = objectMapper.readValue(
                confirmResult.getResponse().getContentAsString(), ConfirmPreviewResponseDto.class);
        assertThat(confirmResponse.applied()).hasSize(1);
        assertThat(confirmResponse.skippedEventIds()).isEmpty();

        Allocation persisted = allocationRepository.findByOccurrenceId(occurrence.getId()).orElseThrow();
        assertThat(persisted.getClassroomId()).isEqualTo(proposedRoom);
        assertThat(persisted.getSource()).isEqualTo(AllocationSource.AUTOMATIC);
        assertThat(occurrenceRepository.findById(occurrence.getId()).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(allocationRepository.findByOccurrenceIdIn(List.of(occurrence.getId()))).hasSize(1);

        mockMvc.perform(post("/v1/previews/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPreviewRequestDto(
                                List.of(new PreviewAllocationDto(eventId, null, proposedRoom))))))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("confirm: conflicto inyectado entre preview y confirm responde 409 y no persiste nada")
    void confirm_conflictInjectedBetweenPreviewAndConfirm_returns409AndPersistsNothing() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(107);
        Long eventId = createEvent(sc, date);
        Occurrence occurrence = occurrenceOf(eventId);

        PreviewResponseDto preview = autoPreview(List.of(eventId));
        assertThat(preview.allocations()).hasSize(1);
        Long proposedRoom = preview.allocations().getFirst().classroom().id();

        var scForeign = testData.materiaYComision();
        Long foreignEventId = createEvent(scForeign, date);
        allocateDirect(occurrenceOf(foreignEventId), proposedRoom);

        mockMvc.perform(post("/v1/previews/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPreviewRequestDto(
                                List.of(new PreviewAllocationDto(eventId, null, proposedRoom))))))
                .andExpect(status().isConflict());

        assertThat(allocationRepository.findByOccurrenceId(occurrence.getId())).isEmpty();
        assertThat(occurrenceRepository.findById(occurrence.getId()).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }

    private Long createUniqueEvent(IntegrationTestData.SubjectAndCommission sc, LocalDate date, int enrolled) {
        var dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.EXAMEN_FINAL, sc.subjectId(), sc.commissionId(),
                date, LocalTime.of(15, 0), DURATION, enrolled, null, null, null, null, null, null);
        return academicEventService.createUniqueEvent(dto).id();
    }

    private ConfirmPreviewRequestDto confirmBody(Long eventId, PreviewResponseDto preview) {
        return new ConfirmPreviewRequestDto(preview.allocations().stream()
                .map(item -> new PreviewAllocationDto(eventId, item.roomSlot(), item.classroom().id()))
                .toList());
    }

    @Test
    @DisplayName("POST /v1/previews con selectAll: un único sin aula ya no responde 409 y recibe propuesta")
    void autoPreview_selectAllWithUnassignedUniqueEvent_returnsProposal() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        Long uniqueId = createUniqueEvent(sc, LocalDate.now().plusDays(140), 20);
        List<Long> otherUnallocatedIds = asFixtureUser(allocationConflictService::resolveAllUnallocatedEventIds)
                .stream().filter(id -> !id.equals(uniqueId)).toList();

        MvcResult result = mockMvc.perform(post("/v1/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PreviewRequestDto(null, true, otherUnallocatedIds, 2))))
                .andExpect(status().isOk())
                .andReturn();
        PreviewResponseDto preview = objectMapper.readValue(
                result.getResponse().getContentAsString(), PreviewResponseDto.class);

        assertThat(preview.allocations()).filteredOn(item -> item.event().id().equals(uniqueId))
                .singleElement().satisfies(item -> {
                    assertThat(item.classroom()).isNotNull();
                    assertThat(item.roomSlot()).isEqualTo(1);
                    assertThat(item.occurrenceDates()).containsExactly(LocalDate.now().plusDays(140));
                });
    }

    @Test
    @DisplayName("POST /v1/previews mixto: recurrente y único en el mismo horario reciben propuesta y aulas distintas")
    void autoPreview_mixedRecurringAndUnique_bothGetDifferentRooms() throws Exception {
        var building = testData.edificio();
        testData.aula(building);
        testData.aula(building);
        LocalDate date = LocalDate.now().plusDays(141);
        Long recurringId = createEvent(testData.materiaYComision(), date);
        var uniqueDto = new CreateUniqueEventRequestDto(
                UniqueEventKind.OTRO, null, null, date, START, DURATION, 20, null, null, null, null, null, null);
        Long uniqueId = academicEventService.createUniqueEvent(uniqueDto).id();

        PreviewResponseDto preview = autoPreview(List.of(recurringId, uniqueId));

        assertThat(preview.unresolved()).isEmpty();
        assertThat(preview.allocations()).extracting(item -> item.event().id())
                .containsExactlyInAnyOrder(recurringId, uniqueId);
        assertThat(preview.allocations()).extracting(item -> item.classroom().id()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("confirm: un único con 2 roomSlot asigna una aula distinta a cada ocurrencia simultánea")
    void confirm_uniqueEventWithTwoRoomSlots_assignsTwoDifferentRooms() throws Exception {
        var building = testData.edificio();
        testData.aula(building);
        testData.aula(building);
        Long uniqueId = createUniqueEvent(testData.materiaYComision(), LocalDate.now().plusDays(142), 20);
        Long principalId = occurrenceOf(uniqueId).getId();
        Long mirrorId = occurrenceService.createSimultaneous(principalId, 1).getFirst();

        PreviewResponseDto preview = autoPreview(List.of(uniqueId));
        assertThat(preview.allocations()).extracting(PreviewItemDto::roomSlot).containsExactlyInAnyOrder(1, 2);
        assertThat(preview.allocations()).extracting(item -> item.classroom().id()).doesNotHaveDuplicates();

        MvcResult confirmResult = mockMvc.perform(post("/v1/previews/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmBody(uniqueId, preview))))
                .andExpect(status().isOk())
                .andReturn();
        ConfirmPreviewResponseDto confirmResponse = objectMapper.readValue(
                confirmResult.getResponse().getContentAsString(), ConfirmPreviewResponseDto.class);

        assertThat(confirmResponse.applied()).hasSize(2);
        Long roomOfPrincipal = allocationRepository.findByOccurrenceId(principalId).orElseThrow().getClassroomId();
        Long roomOfMirror = allocationRepository.findByOccurrenceId(mirrorId).orElseThrow().getClassroomId();
        assertThat(roomOfPrincipal).isNotEqualTo(roomOfMirror);
        PreviewItemDto slotOne = preview.allocations().stream().filter(i -> i.roomSlot() == 1).findFirst().orElseThrow();
        PreviewItemDto slotTwo = preview.allocations().stream().filter(i -> i.roomSlot() == 2).findFirst().orElseThrow();
        assertThat(roomOfPrincipal).isEqualTo(slotOne.classroom().id());
        assertThat(roomOfMirror).isEqualTo(slotTwo.classroom().id());
    }

    @Test
    @DisplayName("confirm: dos roomSlot del mismo evento en la misma aula responde 409 y no persiste nada")
    void confirm_twoRoomSlotsInSameRoom_returns409AndPersistsNothing() throws Exception {
        var building = testData.edificio();
        Classroom room = testData.aula(building);
        testData.aula(building);
        Long uniqueId = createUniqueEvent(testData.materiaYComision(), LocalDate.now().plusDays(143), 20);
        Long principalId = occurrenceOf(uniqueId).getId();
        Long mirrorId = occurrenceService.createSimultaneous(principalId, 1).getFirst();
        PreviewResponseDto preview = autoPreview(List.of(uniqueId));

        mockMvc.perform(post("/v1/previews/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPreviewRequestDto(List.of(
                                new PreviewAllocationDto(uniqueId, 1, room.getId()),
                                new PreviewAllocationDto(uniqueId, 2, room.getId()))))))
                .andExpect(status().isConflict());

        assertThat(allocationRepository.findByOccurrenceId(principalId)).isEmpty();
        assertThat(allocationRepository.findByOccurrenceId(mirrorId)).isEmpty();
    }

    @Test
    @DisplayName("confirm: una ocurrencia ROOM_RELEASED de un recurrente no entra al preview ni se reasigna")
    void confirm_releasedOccurrence_isNotReassigned() throws Exception {
        testData.aula(testData.edificio());
        var sc = testData.materiaYComision();
        LocalDate first = LocalDate.now().plusDays(144);
        LocalDate second = first.plusDays(7);
        Long eventId = academicEventService.createRecurringEvent(new CreateRecurringEventRequestDto(
                30, START, DURATION, first.getDayOfWeek(), first, second, sc.subjectId(), sc.commissionId())).id();
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(Comparator.comparing(Occurrence::getDate)).toList();
        assertThat(occurrences).hasSize(2);
        Long pendingId = occurrences.get(0).getId();
        Long releasedId = occurrences.get(1).getId();
        occurrenceService.release(List.of(new OccurrenceRelease(releasedId, null)), ReleaseReason.VIRTUAL_CLASS, null);

        PreviewResponseDto preview = autoPreview(List.of(eventId));
        assertThat(preview.allocations()).singleElement()
                .satisfies(item -> assertThat(item.occurrenceDates()).containsExactly(first));

        mockMvc.perform(post("/v1/previews/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmBody(eventId, preview))))
                .andExpect(status().isOk());

        assertThat(allocationRepository.findByOccurrenceId(pendingId)).isPresent();
        assertThat(allocationRepository.findByOccurrenceId(releasedId)).isEmpty();
        assertThat(occurrenceRepository.findById(releasedId).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.ROOM_RELEASED);
    }
}
