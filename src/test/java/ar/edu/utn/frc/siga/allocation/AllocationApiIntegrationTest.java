package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DisplayName("Allocation API (integración)")
class AllocationApiIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(8, 0);
    private static final int DURATION = 90;

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
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Occurrence seedOccurrence(IntegrationTestData.SubjectAndCommission sc, LocalDate date) {
        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        assertThat(occurrences).hasSize(1);
        return occurrences.getFirst();
    }

    private static AllocationBatchRequestDto byOccurrence(Long occurrenceId, Long classroomId) {
        return new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)), null);
    }

    private static AllocationBatchRequestDto byEvent(Long eventId, Long classroomId, String observation) {
        return new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(null, eventId, null, null, classroomId)), observation);
    }

    private static AllocationBatchRequestDto byRange(Long eventId, LocalDate from, LocalDate to, Long classroomId) {
        return new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(null, eventId, from, to, classroomId)), "movimiento por rango");
    }

    /** Evento semanal de {@code weeks} ocurrencias arrancando en {@code start}. */
    private Long seedWeeklyEvent(IntegrationTestData.SubjectAndCommission sc, LocalDate start, int weeks) {
        var dto = new CreateRecurringEventRequestDto(30, START, DURATION, start.getDayOfWeek(),
                start, start.plusWeeks(weeks - 1L), sc.subjectId(), sc.commissionId());
        return academicEventService.createRecurringEvent(dto).id();
    }

    private Long classroomOf(Long occurrenceId) {
        return allocationRepository.findByOccurrenceIdIn(List.of(occurrenceId)).stream()
                .map(Allocation::getClassroomId).findFirst().orElse(null);
    }

    private MvcResult allocateOk(Long occurrenceId, Long classroomId) throws Exception {
        return mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byOccurrence(occurrenceId, classroomId))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private long allocationIdFrom(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(0).get("id").asLong();
    }

    @Test
    @DisplayName("POST /v1/allocations crea la asignación MANUAL; el estado de la ocurrencia no cambia (es derivado, ortogonal al enum)")
    void allocateManually_persistsManualAllocationWithoutChangingOccurrenceStatus() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        Occurrence occurrence = seedOccurrence(sc, LocalDate.now().plusDays(7));

        long allocationId = allocationIdFrom(allocateOk(occurrence.getId(), aula.getId()));

        Allocation saved = allocationRepository.findById(allocationId).orElseThrow();
        assertThat(saved.getClassroomId()).isEqualTo(aula.getId());
        assertThat(saved.getSource()).isEqualTo(AllocationSource.MANUAL);
        assertThat(occurrenceRepository.findById(occurrence.getId()).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }

    @Test
    @DisplayName("Asignar dos veces la misma ocurrencia responde 409")
    void allocateManually_twiceOnSameOccurrence_returns409() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        Occurrence occurrence = seedOccurrence(sc, LocalDate.now().plusDays(8));

        allocateOk(occurrence.getId(), aula.getId());

        mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byOccurrence(occurrence.getId(), aula.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Allocation error"));
    }

    @Test
    @DisplayName("Asignar una ocurrencia pasada responde 409")
    void allocateManually_pastOccurrence_returns409() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RecurringEvent event = eventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(START).duration(Duration.ofMinutes(DURATION))
                .dayOfWeek(yesterday.getDayOfWeek()).startDate(yesterday).endDate(yesterday)
                .subjectId(999_999L).commissionId(999_999L)
                .build());
        Occurrence past = occurrenceRepository.save(Occurrence.builder()
                .event(event).date(yesterday).status(OccurrenceStatus.NEEDS_ROOM).build());

        mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byOccurrence(past.getId(), aula.getId()))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Asignar sobre una franja ya ocupada por otro evento en la misma aula responde 409 con ProblemDetail")
    void allocateManually_overlappingSlotSameClassroom_returns409() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(9);
        Occurrence first = seedOccurrence(sc, date);
        Occurrence second = seedOccurrence(sc, date);

        allocateOk(first.getId(), aula.getId());

        mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byOccurrence(second.getId(), aula.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reallocation conflict"))
                .andExpect(jsonPath("$.conflicts").isArray());
    }

    @Test
    @DisplayName("PUT /v1/allocations reasigna el aula de una ocurrencia y persiste el cambio")
    void reallocate_changesClassroomInDatabase() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaOriginal = testData.aula(edificio);
        Classroom aulaNueva = testData.aula(edificio);
        Occurrence occurrence = seedOccurrence(sc, LocalDate.now().plusDays(10));

        long allocationId = allocationIdFrom(allocateOk(occurrence.getId(), aulaOriginal.getId()));

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byOccurrence(occurrence.getId(), aulaNueva.getId()))))
                .andExpect(status().isOk());

        assertThat(allocationRepository.findById(allocationId).orElseThrow().getClassroomId())
                .isEqualTo(aulaNueva.getId());
    }

    @Test
    @DisplayName("PUT /v1/allocations con un item del lote en conflicto responde 409 y NO aplica ningún cambio")
    void batchReallocate_oneConflictingMove_appliesNothing() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaA = testData.aula(edificio);
        Classroom aulaB = testData.aula(edificio);
        Classroom aulaC = testData.aula(edificio);
        Classroom aulaLibre = testData.aula(edificio);
        LocalDate date = LocalDate.now().plusDays(11);

        Occurrence occ1 = seedOccurrence(sc, date);
        Occurrence occ2 = seedOccurrence(sc, date);
        Occurrence occ3 = seedOccurrence(sc, date);
        long alloc1 = allocationIdFrom(allocateOk(occ1.getId(), aulaA.getId()));
        long alloc2 = allocationIdFrom(allocateOk(occ2.getId(), aulaB.getId()));
        allocateOk(occ3.getId(), aulaC.getId());

        var dto = new AllocationBatchRequestDto(List.of(
                new AllocationItemRequestDto(List.of(occ1.getId()), null, null, null, aulaLibre.getId()),
                new AllocationItemRequestDto(List.of(occ2.getId()), null, null, null, aulaC.getId())), null);

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());

        assertThat(allocationRepository.findById(alloc1).orElseThrow().getClassroomId()).isEqualTo(aulaA.getId());
        assertThat(allocationRepository.findById(alloc2).orElseThrow().getClassroomId()).isEqualTo(aulaB.getId());
    }

    @Test
    @DisplayName("PUT /v1/allocations por evento con solape responde 409 con el detalle del conflicto")
    void reallocateByEvent_withOverlap_returns409WithDetail() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(12);
        allocateOk(seedOccurrence(sc, date).getId(), aula.getId());

        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date.plusWeeks(2), sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byEvent(eventId, aula.getId(), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts").isArray());

        assertThat(allocationRepository.findByOccurrenceIdIn(
                occurrenceRepository.findByEvent_Id(eventId).stream().map(Occurrence::getId).toList()))
                .isEmpty();
    }

    @Test
    @DisplayName("PUT /v1/allocations por evento sin solape asigna todas las ocurrencias futuras")
    void reallocateByEvent_withoutOverlap_allocatesFutureOccurrences() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(13);

        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date.plusWeeks(2), sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byEvent(eventId, aula.getId(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        assertThat(occurrences).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM));
        assertThat(allocationRepository.findByOccurrenceIdIn(
                occurrences.stream().map(Occurrence::getId).toList()))
                .hasSize(3)
                .allSatisfy(a -> {
                    assertThat(a.getClassroomId()).isEqualTo(aula.getId());
                    assertThat(a.getSource()).isEqualTo(AllocationSource.MANUAL);
                });
    }

    @Test
    @DisplayName("PUT /v1/allocations por evento reasigna solo las ocurrencias futuras y deja intactas las pasadas")
    void reallocateByEvent_happyPath_reallocatesFutureOnly() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaOriginal = testData.aula(edificio);
        Classroom aulaNueva = testData.aula(edificio);
        LocalDate date = LocalDate.now().plusDays(15);

        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date.plusWeeks(2), sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();

        Occurrence past = occurrenceRepository.save(Occurrence.builder()
                .event(eventRepository.findById(eventId).orElseThrow())
                .date(LocalDate.now().minusDays(7))
                .status(OccurrenceStatus.NEEDS_ROOM)
                .build());
        long pastAllocationId = allocationRepository.save(Allocation.builder()
                .occurrenceId(past.getId())
                .classroomId(aulaOriginal.getId())
                .source(AllocationSource.MANUAL)
                .build()).getId();

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byEvent(eventId, aulaNueva.getId(), "reasignación de evento"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        List<Occurrence> futureOccurrences = occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(eventId, LocalDate.now());
        assertThat(allocationRepository.findByOccurrenceIdIn(futureOccurrences.stream().map(Occurrence::getId).toList()))
                .hasSize(3)
                .allSatisfy(a -> assertThat(a.getClassroomId()).isEqualTo(aulaNueva.getId()));

        assertThat(allocationRepository.findById(pastAllocationId).orElseThrow().getClassroomId())
                .isEqualTo(aulaOriginal.getId());
    }

    @Test
    @DisplayName("PUT /v1/allocations por evento sin ocurrencias futuras es un no-op (no toca el pasado, KB: no modificar el pasado)")
    void reallocateByEvent_onlyPastOccurrences_isNoOp() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RecurringEvent event = eventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(START).duration(Duration.ofMinutes(DURATION))
                .dayOfWeek(yesterday.getDayOfWeek()).startDate(yesterday).endDate(yesterday)
                .subjectId(999_999L).commissionId(999_999L)
                .build());
        Occurrence past = occurrenceRepository.save(Occurrence.builder()
                .event(event).date(yesterday).status(OccurrenceStatus.NEEDS_ROOM).build());

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byEvent(event.getId(), aula.getId(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(allocationRepository.findByOccurrenceIdIn(List.of(past.getId()))).isEmpty();
    }

    // ---------- reasignación por rango (temporal y permanente) ----------

    @Test
    @DisplayName("PUT por rango temporal mueve solo las ocurrencias de la ventana y deja el resto en su aula original")
    void reallocateByRange_temporal_movesOnlyOccurrencesInsideWindow() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaOriginal = testData.aula(edificio);
        Classroom aulaNueva = testData.aula(edificio);
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(sc, start, 5);

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byEvent(eventId, aulaOriginal.getId(), null))))
                .andExpect(status().isOk());

        // Ventana sobre la 2da y 3ra ocurrencia: bordes inclusive.
        LocalDate from = start.plusWeeks(1);
        LocalDate to = start.plusWeeks(2);

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byRange(eventId, from, to, aulaNueva.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(java.util.Comparator.comparing(Occurrence::getDate)).toList();
        assertThat(occurrences).hasSize(5);
        assertThat(classroomOf(occurrences.get(0).getId())).isEqualTo(aulaOriginal.getId());
        assertThat(classroomOf(occurrences.get(1).getId())).isEqualTo(aulaNueva.getId());
        assertThat(classroomOf(occurrences.get(2).getId())).isEqualTo(aulaNueva.getId());
        assertThat(classroomOf(occurrences.get(3).getId())).isEqualTo(aulaOriginal.getId());
        assertThat(classroomOf(occurrences.get(4).getId())).isEqualTo(aulaOriginal.getId());
    }

    @Test
    @DisplayName("PUT por rango sin 'to' es permanente: mueve desde esa fecha en adelante y no toca lo anterior")
    void reallocateByRange_sinTo_esPermanenteDesdeLaFecha() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaOriginal = testData.aula(edificio);
        Classroom aulaNueva = testData.aula(edificio);
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(sc, start, 4);

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byEvent(eventId, aulaOriginal.getId(), null))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                byRange(eventId, start.plusWeeks(2), null, aulaNueva.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId).stream()
                .sorted(java.util.Comparator.comparing(Occurrence::getDate)).toList();
        assertThat(classroomOf(occurrences.get(0).getId())).isEqualTo(aulaOriginal.getId());
        assertThat(classroomOf(occurrences.get(1).getId())).isEqualTo(aulaOriginal.getId());
        assertThat(classroomOf(occurrences.get(2).getId())).isEqualTo(aulaNueva.getId());
        assertThat(classroomOf(occurrences.get(3).getId())).isEqualTo(aulaNueva.getId());
    }

    @Test
    @DisplayName("PUT por rango con 'from' anterior a hoy responde 400 (D4: el pedido está mal formado)")
    void reallocateByRange_fromEnElPasado_responde400() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(sc, start, 2);

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                byRange(eventId, LocalDate.now().minusDays(1), null, aula.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT por rango con 'to' anterior a 'from' responde 400")
    void reallocateByRange_toAntesQueFrom_responde400() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(sc, start, 2);

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                byRange(eventId, start.plusWeeks(1), start, aula.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT con 'from' junto a occurrenceIds responde 400: el rango solo aplica sobre un evento")
    void reallocateByRange_rangoSobreOccurrenceIds_responde400() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        Occurrence occ = seedOccurrence(sc, LocalDate.now().plusDays(7));
        var dto = new AllocationBatchRequestDto(List.of(new AllocationItemRequestDto(
                List.of(occ.getId()), null, LocalDate.now().plusDays(1), null, aula.getId())), null);

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT por rango con una sola fecha en conflicto responde 409 y NO mueve ninguna (D5: todo o nada)")
    void reallocateByRange_unaFechaEnConflicto_noAplicaNada() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaOriginal = testData.aula(edificio);
        Classroom aulaDestino = testData.aula(edificio);
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(sc, start, 3);

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(byEvent(eventId, aulaOriginal.getId(), null))))
                .andExpect(status().isOk());

        // Un evento ajeno ocupa el aula destino en la fecha del medio, misma franja.
        var otro = testData.materiaYComision();
        Occurrence intruso = seedOccurrence(otro, start.plusWeeks(1));
        allocateOk(intruso.getId(), aulaDestino.getId());

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                byRange(eventId, start, start.plusWeeks(2), aulaDestino.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts").isArray());

        assertThat(occurrenceRepository.findByEvent_Id(eventId))
                .allSatisfy(o -> assertThat(classroomOf(o.getId())).isEqualTo(aulaOriginal.getId()));
    }

    @Test
    @DisplayName("Un lote puede mezclar rango y ocurrencias puntuales en la misma operación atómica")
    void reallocate_mezclaRangoYPuntualEnElMismoLote() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaRango = testData.aula(edificio);
        Classroom aulaPuntual = testData.aula(edificio);
        LocalDate start = LocalDate.now().plusDays(7);
        Long eventId = seedWeeklyEvent(sc, start, 3);
        Occurrence suelta = seedOccurrence(testData.materiaYComision(), start.plusDays(1));

        var dto = new AllocationBatchRequestDto(List.of(
                new AllocationItemRequestDto(null, eventId, start, start.plusWeeks(1), aulaRango.getId()),
                new AllocationItemRequestDto(List.of(suelta.getId()), null, null, null, aulaPuntual.getId())), null);

        mockMvc.perform(put("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        assertThat(classroomOf(suelta.getId())).isEqualTo(aulaPuntual.getId());
    }

    @Test
    @DisplayName("Envers audita el agregado: crear evento y asignar deja filas en las tablas _aud tras el commit real")
    void envers_auditsAggregateAfterRealCommit() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(14);
        Occurrence occurrence = seedOccurrence(sc, date);
        Long eventId = occurrence.getEvent().getId();

        long allocationId = allocationIdFrom(allocateOk(occurrence.getId(), aula.getId()));

        Integer eventRevs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evento_academico_aud WHERE id_evento_academico = ?", Integer.class, eventId);
        Integer occurrenceRevs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ocurrencia_aud WHERE id_ocurrencia = ?", Integer.class, occurrence.getId());
        Integer allocationRevs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asignacion_aula_aud WHERE id_asignacion = ?", Integer.class, allocationId);

        assertThat(eventRevs).isGreaterThanOrEqualTo(1);          // ADD al crear
        assertThat(occurrenceRevs).isGreaterThanOrEqualTo(1);     // ADD al crear (asignar ya no muta la ocurrencia)
        assertThat(allocationRevs).isGreaterThanOrEqualTo(1);     // ADD al asignar

        String usuario = jdbcTemplate.queryForObject(
                "SELECT r.usuario FROM revinfo r JOIN asignacion_aula_aud a ON a.rev = r.rev "
                        + "WHERE a.id_asignacion = ? ORDER BY r.rev LIMIT 1",
                String.class, allocationId);
        assertThat(usuario).isEqualTo("integration-test@frc.utn.edu.ar");
    }
}
