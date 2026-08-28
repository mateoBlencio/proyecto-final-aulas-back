package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.repository.UniqueEventRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración de {@code GET /v1/allocations/conflicts} contra Postgres real: los tres tipos
 * de conflicto (unallocated/overcrowded/overlap) filtrados por {@code types}, el rango por
 * defecto (fin del período académico activo) y el 400 por rango inválido.
 */
@Import(IntegrationTestData.class)
@DisplayName("Allocation Conflicts API (integración)")
class AllocationProblemsIntegrationTest extends AbstractIntegrationTest {

    private static final LocalTime START = LocalTime.of(9, 0);
    private static final int DURATION = 60;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private UniqueEventRepository uniqueEventRepository;
    @Autowired
    private AcademicPeriodService academicPeriodService;
    @Autowired
    private ObjectMapper objectMapper;

    private Occurrence seedOccurrence(IntegrationTestData.SubjectAndCommission sc, LocalDate date, Integer enrolled) {
        var dto = new CreateRecurringEventRequestDto(
                enrolled, START, DURATION, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();
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

    /** Segunda allocation en la misma franja/aula por repositorio directo: la API bloquea el solape (validateNoOverlap). */
    private void allocateDirect(Occurrence occurrence, Long classroomId) {
        allocationRepository.save(Allocation.builder()
                .occurrenceId(occurrence.getId()).classroomId(classroomId).source(AllocationSource.MANUAL).build());
    }

    /**
     * Siembra un evento único SCHEDULED sin aula, directo por repositorio: el endpoint
     * {@code POST /v1/events/unique} exige aula obligatoria (alta atómica), así que este
     * caso -pensado solo para ejercitar el listado de "unallocated"- construye la entidad a
     * mano en vez de pasar por el service.
     */
    private Long seedUniqueEventWithoutClassroom(LocalDate date) {
        UniqueEvent event = UniqueEvent.builder()
                .enrolled(20).startTime(START).duration(java.time.Duration.ofMinutes(DURATION))
                .date(date).description("Evento en el límite del período")
                .kind(ar.edu.utn.frc.siga.events.model.UniqueEventKind.OTRO)
                .build();
        AcademicEvent saved = uniqueEventRepository.save(event);
        occurrenceRepository.saveAll(saved.toOccurrences());
        return saved.getId();
    }

    private JsonNode conflictsContent(String types, LocalDate from, LocalDate to) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/allocations/conflicts")
                        .param("types", types)
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
    }

    @Test
    @DisplayName("types=UNALLOCATED devuelve el evento sembrado sin ninguna asignación")
    void unallocatedConflicts_returnSeededEvent() throws Exception {
        LocalDate from = LocalDate.now().plusDays(60);
        LocalDate to = LocalDate.now().plusDays(70);
        var sc = testData.materiaYComision();
        Occurrence unallocatedOcc = seedOccurrence(sc, from.plusDays(1), 30);
        Long unallocatedEventId = unallocatedOcc.getEvent().getId();

        JsonNode content = conflictsContent("UNALLOCATED", from, to);

        assertThat(content).anySatisfy(node -> assertThat(node.get("event").get("id").asLong()).isEqualTo(unallocatedEventId));
    }

    @Test
    @DisplayName("types=OVERCROWDED devuelve el evento cuyos inscriptos superan la capacidad del aula asignada")
    void overcrowdedConflicts_returnSeededEvent() throws Exception {
        LocalDate from = LocalDate.now().plusDays(60);
        LocalDate to = LocalDate.now().plusDays(70);
        var sc = testData.materiaYComision();
        Classroom aulaChica = testData.aula(testData.edificio(), testData.tipoAulaNormal(), 10);
        Occurrence overcrowdOcc = seedOccurrence(sc, from.plusDays(2), 50);
        Long overcrowdEventId = overcrowdOcc.getEvent().getId();
        allocateOk(overcrowdOcc.getId(), aulaChica.getId());

        JsonNode content = conflictsContent("OVERCROWDED", from, to);

        assertThat(content).anySatisfy(node -> {
            assertThat(node.get("event").get("id").asLong()).isEqualTo(overcrowdEventId);
            assertThat(node.get("classroom").get("id").asLong()).isEqualTo(aulaChica.getId());
            assertThat(node.get("enrolled").asInt()).isEqualTo(50);
            assertThat(node.get("capacity").asInt()).isEqualTo(10);
            assertThat(node.get("overcrowdedBy").asInt()).isEqualTo(40);
        });
    }

    @Test
    @DisplayName("types=OVERLAP devuelve el par de eventos que comparten aula y horario")
    void overlapConflicts_returnSeededPair() throws Exception {
        LocalDate from = LocalDate.now().plusDays(60);
        LocalDate to = LocalDate.now().plusDays(70);
        var scOverlapA = testData.materiaYComision();
        var scOverlapB = testData.materiaYComision();
        Classroom aulaOverlap = testData.aula(testData.edificio(), testData.tipoAulaNormal(), 100);
        LocalDate overlapDate = from.plusDays(3);
        Occurrence overlapOccA = seedOccurrence(scOverlapA, overlapDate, 20);
        Occurrence overlapOccB = seedOccurrence(scOverlapB, overlapDate, 20);
        allocateOk(overlapOccA.getId(), aulaOverlap.getId());
        allocateDirect(overlapOccB, aulaOverlap.getId());
        Long overlapEventAId = overlapOccA.getEvent().getId();
        Long overlapEventBId = overlapOccB.getEvent().getId();

        JsonNode content = conflictsContent("OVERLAP", from, to);

        assertThat(content).anySatisfy(node -> {
            assertThat(node.get("classroom").get("id").asLong()).isEqualTo(aulaOverlap.getId());
            assertThat(List.of(node.get("eventA").get("id").asLong(), node.get("eventB").get("id").asLong()))
                    .containsExactlyInAnyOrder(overlapEventAId, overlapEventBId);
        });
    }

    @Test
    @DisplayName("GET /v1/allocations/conflicts sin 'to' usa el fin del período académico activo con mayor endDate")
    void conflicts_withoutTo_usesActivePeriodEndDate() throws Exception {
        // Año deliberadamente muy lejano: garantiza ser el endDate máximo entre todos los períodos
        // activos existentes (los de otros tests usan años <= 2600), sin importar el orden de ejecución.
        int year = 9000 + (int) (IntegrationTestData.nextSeq() % 500);
        var period = academicPeriodService.findOrCreate(year, TermType.ANUAL).value();

        Long eventId = seedUniqueEventWithoutClassroom(period.endDate());

        mockMvc.perform(get("/v1/allocations/conflicts").param("types", "UNALLOCATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.event.id == " + eventId + ")]").exists());
    }

    @Test
    @DisplayName("GET /v1/allocations/conflicts con to<from responde 400")
    void conflicts_invalidRange_returns400() throws Exception {
        LocalDate from = LocalDate.now().plusDays(10);
        LocalDate to = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/v1/allocations/conflicts")
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid date range"));
    }
}
