package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.events.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.events.service.AcademicEventService;
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

/**
 * Integración de asignaciones manuales contra Postgres real: validación de solapamiento
 * ({@code validateNoOverlap}) con la query de ocupación real, atomicidad de
 * {@code @Transactional} en el lote, y auditoría Envers con commits reales.
 */
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

    /**
     * Evento recurrente de 1 sola ocurrencia en {@code date} (dayOfWeek == date.getDayOfWeek()),
     * creado por el servicio real (valida materia/comisión contra las fachadas).
     */
    private Occurrence seedOccurrence(IntegrationTestData.SubjectAndCommission sc, LocalDate date) {
        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        assertThat(occurrences).hasSize(1);
        return occurrences.getFirst();
    }

    private MvcResult assignOk(Long occurrenceId, Integer classroomId) throws Exception {
        return mockMvc.perform(post("/v1/allocations/occurrences/{id}", occurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(classroomId, null))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private long allocationIdFrom(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("POST /v1/allocations/occurrences/{id} crea la asignación MANUAL y la ocurrencia pasa a ASSIGNED")
    void assignManually_persistsManualAllocationAndMarksOccurrenceAssigned() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        Occurrence occurrence = seedOccurrence(sc, LocalDate.now().plusDays(7));

        long allocationId = allocationIdFrom(assignOk(occurrence.getId(), aula.getId()));

        Allocation saved = allocationRepository.findById(allocationId).orElseThrow();
        assertThat(saved.getClassroomId()).isEqualTo(aula.getId());
        assertThat(saved.getSource()).isEqualTo(AllocationSource.MANUAL);
        assertThat(occurrenceRepository.findById(occurrence.getId()).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.ASSIGNED);
    }

    @Test
    @DisplayName("Asignar dos veces la misma ocurrencia responde 409")
    void assignManually_twiceOnSameOccurrence_returns409() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        Occurrence occurrence = seedOccurrence(sc, LocalDate.now().plusDays(8));

        assignOk(occurrence.getId(), aula.getId());

        mockMvc.perform(post("/v1/allocations/occurrences/{id}", occurrence.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(aula.getId(), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Allocation error"));
    }

    @Test
    @DisplayName("Asignar una ocurrencia pasada responde 409")
    void assignManually_pastOccurrence_returns409() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        // Sembrada por repositorio (el endpoint de creación no genera ocurrencias pasadas):
        // ids de materia/comisión planos sin FK, no hacen falta filas reales de academic.
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RecurringEvent event = eventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(START).duration(Duration.ofMinutes(DURATION))
                .dayOfWeek(yesterday.getDayOfWeek()).startDate(yesterday).endDate(yesterday)
                .subjectId(999_999L).commissionId(999_999L)
                .build());
        Occurrence past = occurrenceRepository.save(Occurrence.builder()
                .event(event).date(yesterday).status(OccurrenceStatus.SCHEDULED).build());

        mockMvc.perform(post("/v1/allocations/occurrences/{id}", past.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(aula.getId(), null))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Asignar sobre una franja ya ocupada por otro evento en la misma aula responde 409 con ProblemDetail")
    void assignManually_overlappingSlotSameClassroom_returns409() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(9);
        Occurrence first = seedOccurrence(sc, date);
        Occurrence second = seedOccurrence(sc, date); // mismo día y hora, otro evento

        assignOk(first.getId(), aula.getId());

        mockMvc.perform(post("/v1/allocations/occurrences/{id}", second.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(aula.getId(), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reassign conflict"))
                .andExpect(jsonPath("$.conflicts").isArray());
    }

    @Test
    @DisplayName("PUT /v1/allocations/{id} reasigna el aula y persiste el cambio")
    void reassign_changesClassroomInDatabase() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaOriginal = testData.aula(edificio);
        Classroom aulaNueva = testData.aula(edificio);
        Occurrence occurrence = seedOccurrence(sc, LocalDate.now().plusDays(10));

        long allocationId = allocationIdFrom(assignOk(occurrence.getId(), aulaOriginal.getId()));

        mockMvc.perform(put("/v1/allocations/{id}", allocationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(aulaNueva.getId(), null))))
                .andExpect(status().isOk());

        assertThat(allocationRepository.findById(allocationId).orElseThrow().getClassroomId())
                .isEqualTo(aulaNueva.getId());
    }

    @Test
    @DisplayName("PUT /v1/allocations/batch con un move en conflicto responde 409 y NO aplica ningún move")
    void batchReassign_oneConflictingMove_appliesNothing() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaA = testData.aula(edificio);
        Classroom aulaB = testData.aula(edificio);
        Classroom aulaC = testData.aula(edificio);
        Classroom aulaLibre = testData.aula(edificio);
        LocalDate date = LocalDate.now().plusDays(11);

        long alloc1 = allocationIdFrom(assignOk(seedOccurrence(sc, date).getId(), aulaA.getId()));
        long alloc2 = allocationIdFrom(assignOk(seedOccurrence(sc, date).getId(), aulaB.getId()));
        assignOk(seedOccurrence(sc, date).getId(), aulaC.getId()); // ocupa C en la misma franja

        // move1 es válido (a aula libre); move2 choca contra la ocupación firme de aulaC.
        var dto = new BatchReassignRequestDto(List.of(
                new BatchReassignRequestDto.MoveDto(alloc1, aulaLibre.getId()),
                new BatchReassignRequestDto.MoveDto(alloc2, aulaC.getId())));

        mockMvc.perform(put("/v1/allocations/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());

        // Atomicidad real de @Transactional: ninguno de los dos moves quedó aplicado.
        assertThat(allocationRepository.findById(alloc1).orElseThrow().getClassroomId()).isEqualTo(aulaA.getId());
        assertThat(allocationRepository.findById(alloc2).orElseThrow().getClassroomId()).isEqualTo(aulaB.getId());
    }

    @Test
    @DisplayName("POST /v1/allocations/from-date con solape responde 409 con el detalle del conflicto")
    void allocateFromDate_withOverlap_returns409WithDetail() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(12);
        // Ocupación firme en la primera semana del rango del evento a asignar.
        assignOk(seedOccurrence(sc, date).getId(), aula.getId());

        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date.plusWeeks(2), sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();

        mockMvc.perform(post("/v1/allocations/from-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AllocateFromDateRequestDto(eventId, date, aula.getId(), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts").isArray());

        assertThat(allocationRepository.findByOccurrence_IdIn(
                occurrenceRepository.findByEvent_Id(eventId).stream().map(Occurrence::getId).toList()))
                .isEmpty();
    }

    @Test
    @DisplayName("POST /v1/allocations/from-date sin solape asigna todas las ocurrencias futuras")
    void allocateFromDate_withoutOverlap_assignsFutureOccurrences() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(13);

        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date.plusWeeks(2), sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();

        mockMvc.perform(post("/v1/allocations/from-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AllocateFromDateRequestDto(eventId, date, aula.getId(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        assertThat(occurrences).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OccurrenceStatus.ASSIGNED));
        assertThat(allocationRepository.findByOccurrence_IdIn(
                occurrences.stream().map(Occurrence::getId).toList()))
                .hasSize(3)
                .allSatisfy(a -> {
                    assertThat(a.getClassroomId()).isEqualTo(aula.getId());
                    assertThat(a.getSource()).isEqualTo(AllocationSource.MANUAL);
                });
    }

    @Test
    @DisplayName("PUT /v1/events/{eventId}/classroom reasigna las ocurrencias futuras y deja intactas las pasadas")
    void reassignEvent_happyPath_reassignsFutureOnly() throws Exception {
        var sc = testData.materiaYComision();
        var edificio = testData.edificio();
        Classroom aulaOriginal = testData.aula(edificio);
        Classroom aulaNueva = testData.aula(edificio);
        LocalDate date = LocalDate.now().plusDays(15);

        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date.plusWeeks(2), sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();

        // Ocurrencia pasada del mismo evento, sembrada directo por repositorio (la creación
        // vía servicio solo genera ocurrencias desde hoy en adelante); se le asigna el aula
        // original para verificar que reassignEvent no la toca.
        Occurrence past = occurrenceRepository.save(Occurrence.builder()
                .event(eventRepository.findById(eventId).orElseThrow())
                .date(LocalDate.now().minusDays(7))
                .status(OccurrenceStatus.ASSIGNED)
                .build());
        long pastAllocationId = allocationRepository.save(Allocation.builder()
                .occurrence(past)
                .classroomId(aulaOriginal.getId())
                .source(AllocationSource.MANUAL)
                .createdAt(java.time.LocalDateTime.now())
                .build()).getId();

        mockMvc.perform(put("/v1/events/{eventId}/classroom", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(aulaNueva.getId(), "reasignación de evento"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        List<Occurrence> futureOccurrences = occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(eventId, LocalDate.now());
        assertThat(allocationRepository.findByOccurrence_IdIn(futureOccurrences.stream().map(Occurrence::getId).toList()))
                .hasSize(3)
                .allSatisfy(a -> assertThat(a.getClassroomId()).isEqualTo(aulaNueva.getId()));

        // La ocurrencia pasada queda intacta.
        assertThat(allocationRepository.findById(pastAllocationId).orElseThrow().getClassroomId())
                .isEqualTo(aulaOriginal.getId());
    }

    @Test
    @DisplayName("PUT /v1/events/{eventId}/classroom sobre un evento ya finalizado responde 409")
    void reassignEvent_finishedEvent_returns409() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RecurringEvent event = eventRepository.save(RecurringEvent.builder()
                .enrolled(30).startTime(START).duration(Duration.ofMinutes(DURATION))
                .dayOfWeek(yesterday.getDayOfWeek()).startDate(yesterday).endDate(yesterday)
                .subjectId(999_999L).commissionId(999_999L)
                .build());
        occurrenceRepository.save(Occurrence.builder()
                .event(event).date(yesterday).status(OccurrenceStatus.SCHEDULED).build());

        mockMvc.perform(put("/v1/events/{eventId}/classroom", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(aula.getId(), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Allocation error"));
    }

    @Test
    @DisplayName("Envers audita el agregado: crear evento y asignar deja filas en las tablas _aud tras el commit real")
    void envers_auditsAggregateAfterRealCommit() throws Exception {
        var sc = testData.materiaYComision();
        Classroom aula = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(14);
        Occurrence occurrence = seedOccurrence(sc, date);
        Long eventId = occurrence.getEvent().getId();

        long allocationId = allocationIdFrom(assignOk(occurrence.getId(), aula.getId()));

        Integer eventRevs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evento_academico_aud WHERE id_evento_academico = ?", Integer.class, eventId);
        Integer occurrenceRevs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ocurrencia_aud WHERE id_ocurrencia = ?", Integer.class, occurrence.getId());
        Integer allocationRevs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asignacion_aula_aud WHERE id_asignacion = ?", Integer.class, allocationId);

        assertThat(eventRevs).isGreaterThanOrEqualTo(1);          // ADD al crear
        assertThat(occurrenceRevs).isGreaterThanOrEqualTo(2);     // ADD al crear + MOD al pasar a ASSIGNED
        assertThat(allocationRevs).isGreaterThanOrEqualTo(1);     // ADD al asignar

        String usuario = jdbcTemplate.queryForObject(
                "SELECT r.usuario FROM revinfo r JOIN asignacion_aula_aud a ON a.rev = r.rev "
                        + "WHERE a.id_asignacion = ? ORDER BY r.rev LIMIT 1",
                String.class, allocationId);
        assertThat(usuario).isEqualTo("integration-test@frc.utn.edu.ar");
    }
}
