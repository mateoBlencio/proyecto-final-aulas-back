package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ConfirmAutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ConfirmAutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ProposedAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ValidateMoveResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración end-to-end del flujo de asignación automática (solver real de Timefold,
 * {@code timeLimitSeconds=2}, {@code unimproved-seconds-limit=1} del perfil integration):
 * preview con re-resolución, validate-move y confirm atómico contra Postgres real.
 */
@Import(IntegrationTestData.class)
@DisplayName("Auto Allocation Flow (integración, solver real)")
class AutoAllocationFlowIntegrationTest extends AbstractIntegrationTest {

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
    private AllocationRepository allocationRepository;
    @Autowired
    private ClassroomService classroomService;
    @Autowired
    private ObjectMapper objectMapper;

    private Long createEvent(IntegrationTestData.SubjectAndCommission sc, LocalDate date, LocalTime start, int duration) {
        var dto = new CreateRecurringEventRequestDto(
                30, start, duration, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        return academicEventService.createRecurringEvent(dto).id();
    }

    private Occurrence occurrenceOf(Long eventId) {
        return occurrenceRepository.findByEvent_Id(eventId).getFirst();
    }

    private void assignOk(Long occurrenceId, Integer classroomId) throws Exception {
        mockMvc.perform(post("/v1/allocations/occurrences/{id}", occurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(classroomId, null))))
                .andExpect(status().isCreated());
    }

    /** Ocupación firme sembrada por repositorio, bypasseando validateNoOverlap (evento ajeno, no seleccionado). */
    private void allocateDirect(Occurrence occurrence, Integer classroomId) {
        allocationRepository.save(Allocation.builder()
                .occurrence(occurrence).classroomId(classroomId).source(AllocationSource.MANUAL)
                .createdAt(LocalDateTime.now()).build());
        occurrence.setStatus(OccurrenceStatus.ASSIGNED);
        occurrenceRepository.save(occurrence);
    }

    /**
     * Bloquea TODAS las aulas disponibles del sistema (pool global compartido entre clases de
     * test) en la fecha/franja dada, para forzar de forma determinística "sin aula posible" sin
     * depender de cuántas aulas hayan sembrado otras clases antes de esta.
     */
    private void blockAllAvailableRooms(LocalDate date, LocalTime start, int durationMinutes) {
        for (ClassroomResponseDto room : classroomService.findAllAvailable()) {
            UniqueEvent blocker = eventRepository.save(UniqueEvent.builder()
                    .enrolled(1).startTime(start).duration(Duration.ofMinutes(durationMinutes))
                    .date(date).description("blocker").build());
            Occurrence occ = occurrenceRepository.save(Occurrence.builder()
                    .event(blocker).date(date).status(OccurrenceStatus.ASSIGNED).build());
            allocationRepository.save(Allocation.builder()
                    .occurrence(occ).classroomId(room.id()).source(AllocationSource.MANUAL)
                    .createdAt(LocalDateTime.now()).build());
        }
    }

    private AutoPreviewResponseDto autoPreview(List<Long> eventIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/allocations/auto-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AutoPreviewRequestDto(eventIds, 2))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AutoPreviewResponseDto.class);
    }

    private ValidateMoveResponseDto validateMove(String previewId, ValidateMoveRequestDto request) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/allocations/auto-preview/{id}/validate-move", previewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ValidateMoveResponseDto.class);
    }

    @Test
    @DisplayName("auto-preview resuelve un evento sin aula con una propuesta factible")
    void autoPreview_resolvesEventWithFeasibleRoom() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(100);
        Long eventId = createEvent(sc, date, START, DURATION);

        AutoPreviewResponseDto preview = autoPreview(List.of(eventId));

        assertThat(preview.previewId()).isNotBlank();
        assertThat(preview.allocations()).hasSize(1);
        assertThat(preview.allocations().getFirst().event().id()).isEqualTo(eventId);
        assertThat(preview.allocations().getFirst().classroom()).isNotNull();
        assertThat(preview.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("auto-preview con todas las aulas ocupadas: el evento (nuevo, sin aula previa) queda en unresolved")
    void autoPreview_allRoomsOccupied_leavesEventUnresolved() throws Exception {
        // Con classroom `allowsUnassigned` + la constraint MEDIUM "asignar todo lo posible",
        // el solver deja el evento sin aula (0 hard) en vez de forzar una que se solapa
        // (−1 hard): ya no hay false-feasible. Como es un evento nuevo (sin aula previa en BD),
        // no aplica el floor de no-regresión → viaja explícito en `unresolved`.
        var sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(101);
        blockAllAvailableRooms(date, START, DURATION);
        Long eventId = createEvent(sc, date, START, DURATION);

        AutoPreviewResponseDto preview = autoPreview(List.of(eventId));

        assertThat(preview.allocations()).isEmpty();
        assertThat(preview.unresolved()).hasSize(1);
        assertThat(preview.unresolved().getFirst().event().id()).isEqualTo(eventId);
        // Todas las aulas del sistema están bloqueadas por "blockers" en BD: cada conflicto
        // reportado debe apuntar a la ocupación firme que le tomó esa aula.
        assertThat(preview.unresolved().getFirst().conflicts()).isNotEmpty();
        assertThat(preview.unresolved().getFirst().conflicts())
                .allSatisfy(c -> assertThat(c.origin()).isEqualTo(MoveConflictDto.ConflictOrigin.DATABASE));
    }

    @Test
    @DisplayName("auto-preview con re-resolución: evento ya asignado cambia de aula si la actual quedó tomada por un ajeno; el aula pinned ajena no se pisa")
    void autoPreview_reResolvesAssignedEventAwayFromPinnedForeignRoom() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaActual = testData.aula(edificio);
        testData.aula(edificio); // alternativa libre garantizada para que el solver pueda reubicar
        LocalDate date = LocalDate.now().plusDays(102);

        Long eventId = createEvent(sc, date, START, DURATION);
        Occurrence occurrence = occurrenceOf(eventId);
        assignOk(occurrence.getId(), aulaActual.getId());

        // Ocupación firme ajena (evento NO seleccionado) que pisa la aula actual del evento en la misma franja.
        var scForeign = testData.materiaYComision();
        Long foreignEventId = createEvent(scForeign, date, START, DURATION);
        allocateDirect(occurrenceOf(foreignEventId), aulaActual.getId());

        AutoPreviewResponseDto preview = autoPreview(List.of(eventId));

        assertThat(preview.allocations()).hasSize(1);
        ProposedAllocationDto proposal = preview.allocations().getFirst();
        assertThat(proposal.event().id()).isEqualTo(eventId);
        assertThat(proposal.classroom()).isNotNull();
        assertThat(proposal.classroom().id()).isNotEqualTo(aulaActual.getId());
    }

    @Test
    @DisplayName("GET /v1/allocations/auto-preview/{id} recupera la propuesta guardada; id inexistente responde 410")
    void getPreview_returnsSavedProposal_andUnknownIdReturns410() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(103);
        Long eventId = createEvent(sc, date, START, DURATION);

        AutoPreviewResponseDto original = autoPreview(List.of(eventId));

        MvcResult result = mockMvc.perform(get("/v1/allocations/auto-preview/{id}", original.previewId()))
                .andExpect(status().isOk())
                .andReturn();
        AutoPreviewResponseDto recovered = objectMapper.readValue(
                result.getResponse().getContentAsString(), AutoPreviewResponseDto.class);
        assertThat(recovered.previewId()).isEqualTo(original.previewId());
        assertThat(recovered.allocations()).hasSize(1);
        assertThat(recovered.allocations().getFirst().event().id()).isEqualTo(eventId);

        mockMvc.perform(get("/v1/allocations/auto-preview/{id}", "prev_no_existe"))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("auto-preview: evento sin ocurrencias pendientes responde 409; UniqueEvent responde 409")
    void autoPreview_noPendingOccurrences_and_uniqueEvent_return409() throws Exception {
        LocalDate pastDate = LocalDate.now().minusMonths(2);
        RecurringEvent pastEvent = eventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(START).duration(Duration.ofMinutes(DURATION))
                .dayOfWeek(pastDate.getDayOfWeek()).startDate(pastDate).endDate(pastDate)
                .subjectId(999_999L).commissionId(999_999L).build());
        occurrenceRepository.save(Occurrence.builder()
                .event(pastEvent).date(pastDate).status(OccurrenceStatus.SCHEDULED).build());

        mockMvc.perform(post("/v1/allocations/auto-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AutoPreviewRequestDto(List.of(pastEvent.getId()), 2))))
                .andExpect(status().isConflict());

        Classroom aulaUnico = testData.aula(testData.edificio());
        var uniqueDto = new CreateUniqueEventRequestDto(
                20, START, DURATION, LocalDate.now().plusDays(104), "Evento unico IT", aulaUnico.getId(), null);
        Long uniqueEventId = academicEventService.createUniqueEvent(uniqueDto).id();

        mockMvc.perform(post("/v1/allocations/auto-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AutoPreviewRequestDto(List.of(uniqueEventId), 2))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("validate-move: conflicto contra BD, conflicto contra el preview, aula libre y previewId inexistente")
    void validateMove_databaseConflict_previewConflict_freeRoom_andUnknownPreview() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaBloqueada = testData.aula(edificio);
        testData.aula(edificio);
        testData.aula(edificio); // margen para que el solver resuelva ambos eventos en aulas distintas
        LocalDate date = LocalDate.now().plusDays(105);

        Long eventAId = createEvent(sc, date, START, DURATION);
        var scB = testData.materiaYComision();
        Long eventBId = createEvent(scB, date, START, DURATION);

        AutoPreviewResponseDto preview = autoPreview(List.of(eventAId, eventBId));
        assertThat(preview.allocations()).hasSize(2);

        Integer roomA = preview.allocations().stream()
                .filter(a -> a.event().id().equals(eventAId)).findFirst().orElseThrow().classroom().id();
        Integer roomB = preview.allocations().stream()
                .filter(a -> a.event().id().equals(eventBId)).findFirst().orElseThrow().classroom().id();
        List<PreviewAllocationDto> currentAllocations = List.of(
                new PreviewAllocationDto(eventAId, roomA), new PreviewAllocationDto(eventBId, roomB));

        // Ocupación firme ajena, sembrada DESPUÉS del preview, en aulaBloqueada misma fecha/franja.
        var scForeign = testData.materiaYComision();
        Long foreignEventId = createEvent(scForeign, date, START, DURATION);
        allocateDirect(occurrenceOf(foreignEventId), aulaBloqueada.getId());

        ValidateMoveResponseDto dbConflict = validateMove(preview.previewId(),
                new ValidateMoveRequestDto(eventAId, aulaBloqueada.getId(), currentAllocations));
        assertThat(dbConflict.valid()).isFalse();
        assertThat(dbConflict.conflicts())
                .anySatisfy(c -> assertThat(c.origin()).isEqualTo(MoveConflictDto.ConflictOrigin.DATABASE));

        ValidateMoveResponseDto previewConflict = validateMove(preview.previewId(),
                new ValidateMoveRequestDto(eventAId, roomB, currentAllocations));
        assertThat(previewConflict.valid()).isFalse();
        assertThat(previewConflict.conflicts())
                .anySatisfy(c -> assertThat(c.origin()).isEqualTo(MoveConflictDto.ConflictOrigin.PREVIEW));

        // Aula creada DESPUÉS del preview: no pudo haber sido elegida por el solver, garantiza estar libre.
        Classroom aulaLibre = testData.aula(edificio);
        ValidateMoveResponseDto free = validateMove(preview.previewId(),
                new ValidateMoveRequestDto(eventAId, aulaLibre.getId(), currentAllocations));
        assertThat(free.valid()).isTrue();
        assertThat(free.conflicts()).isEmpty();

        mockMvc.perform(post("/v1/allocations/auto-preview/{id}/validate-move", "prev_no_existe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ValidateMoveRequestDto(eventAId, aulaLibre.getId(), currentAllocations))))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("confirm: persiste con source AUTOMATIC sin duplicar la allocation existente; re-confirm responde 410")
    void confirm_persistsWithoutDuplicating_andReConfirmReturns410() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        testData.aula(testData.edificio());
        Classroom aulaOriginal = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(106);

        Long eventId = createEvent(sc, date, START, DURATION);
        Occurrence occurrence = occurrenceOf(eventId);
        assignOk(occurrence.getId(), aulaOriginal.getId()); // ya asignada: confirm debe actualizar, no duplicar

        AutoPreviewResponseDto preview = autoPreview(List.of(eventId));
        assertThat(preview.allocations()).hasSize(1);
        Integer proposedRoom = preview.allocations().getFirst().classroom().id();

        MvcResult confirmResult = mockMvc.perform(post("/v1/allocations/auto-preview/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmAutoPreviewRequestDto(
                                List.of(new PreviewAllocationDto(eventId, proposedRoom))))))
                .andExpect(status().isOk())
                .andReturn();
        ConfirmAutoPreviewResponseDto confirmResponse = objectMapper.readValue(
                confirmResult.getResponse().getContentAsString(), ConfirmAutoPreviewResponseDto.class);
        assertThat(confirmResponse.applied()).hasSize(1);
        assertThat(confirmResponse.skippedEventIds()).isEmpty();

        Allocation persisted = allocationRepository.findByOccurrence_Id(occurrence.getId()).orElseThrow();
        assertThat(persisted.getClassroomId()).isEqualTo(proposedRoom);
        assertThat(persisted.getSource()).isEqualTo(AllocationSource.AUTOMATIC);
        assertThat(occurrenceRepository.findById(occurrence.getId()).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.ASSIGNED);
        assertThat(allocationRepository.findByOccurrence_IdIn(List.of(occurrence.getId()))).hasSize(1);

        mockMvc.perform(post("/v1/allocations/auto-preview/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmAutoPreviewRequestDto(
                                List.of(new PreviewAllocationDto(eventId, proposedRoom))))))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("confirm: conflicto inyectado entre preview y confirm responde 409 y no persiste nada")
    void confirm_conflictInjectedBetweenPreviewAndConfirm_returns409AndPersistsNothing() throws Exception {
        var sc = testData.materiaYComision();
        testData.aula(testData.edificio());
        testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(107);
        Long eventId = createEvent(sc, date, START, DURATION);
        Occurrence occurrence = occurrenceOf(eventId);

        AutoPreviewResponseDto preview = autoPreview(List.of(eventId));
        assertThat(preview.allocations()).hasSize(1);
        Integer proposedRoom = preview.allocations().getFirst().classroom().id();

        // Conflicto inyectado DESPUÉS del preview: otro evento toma exactamente esa aula/franja.
        var scForeign = testData.materiaYComision();
        Long foreignEventId = createEvent(scForeign, date, START, DURATION);
        allocateDirect(occurrenceOf(foreignEventId), proposedRoom);

        mockMvc.perform(post("/v1/allocations/auto-preview/{id}/confirm", preview.previewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmAutoPreviewRequestDto(
                                List.of(new PreviewAllocationDto(eventId, proposedRoom))))))
                .andExpect(status().isConflict());

        assertThat(allocationRepository.findByOccurrence_Id(occurrence.getId())).isEmpty();
        assertThat(occurrenceRepository.findById(occurrence.getId()).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.SCHEDULED);
    }
}
