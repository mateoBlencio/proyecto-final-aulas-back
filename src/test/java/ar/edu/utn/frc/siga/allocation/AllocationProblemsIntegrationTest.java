package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ClassroomOverlapDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedAllocationDto;
import ar.edu.utn.frc.siga.allocation.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.events.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.events.repository.UniqueEventRepository;
import ar.edu.utn.frc.siga.allocation.events.service.AcademicEventService;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración de los tres endpoints de problemas de asignación contra Postgres real:
 * {@code GET /v1/allocations/unassigned}, {@code /overcrowded} y {@code /overlaps} sobre
 * datos sembrados, el rango por defecto (fin del período académico activo) y el 400 por
 * rango inválido.
 */
@Import(IntegrationTestData.class)
@DisplayName("Allocation Problems API (integración)")
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

    private void assignOk(Long occurrenceId, Integer classroomId) throws Exception {
        mockMvc.perform(post("/v1/allocations/occurrences/{id}", occurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(classroomId, null))))
                .andExpect(status().isCreated());
    }

    /** Segunda allocation en la misma franja/aula por repositorio directo: la API bloquea el solape (validateNoOverlap). */
    private void allocateDirect(Occurrence occurrence, Integer classroomId) {
        allocationRepository.save(Allocation.builder()
                .occurrence(occurrence).classroomId(classroomId).source(AllocationSource.MANUAL)
                .createdAt(LocalDateTime.now()).build());
        occurrence.setStatus(OccurrenceStatus.ASSIGNED);
        occurrenceRepository.save(occurrence);
    }

    /**
     * Siembra un evento único SCHEDULED sin aula, directo por repositorio: el endpoint
     * {@code POST /v1/events/unique} exige aula obligatoria (alta atómica), así que este
     * caso -pensado solo para ejercitar el listado de "unassigned"- construye la entidad a
     * mano en vez de pasar por el service.
     */
    private Long seedUniqueEventWithoutClassroom(LocalDate date, Integer enrolled) {
        UniqueEvent event = UniqueEvent.builder()
                .enrolled(enrolled).startTime(START).duration(java.time.Duration.ofMinutes(DURATION))
                .date(date).description("Evento en el límite del período")
                .kind(ar.edu.utn.frc.siga.allocation.events.model.UniqueEventKind.OTRO)
                .build();
        AcademicEvent saved = uniqueEventRepository.save(event);
        occurrenceRepository.saveAll(saved.toOccurrences());
        return saved.getId();
    }

    private <T> T[] getList(String path, LocalDate from, LocalDate to, Class<T[]> type) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        return objectMapper.readValue(objectMapper.writeValueAsString(content), type);
    }

    @Test
    @DisplayName("Los tres endpoints devuelven unassigned, overcrowded y overlaps sembrados en el rango explícito")
    void problemEndpoints_explicitRange_returnSeededProblems() throws Exception {
        LocalDate from = LocalDate.now().plusDays(60);
        LocalDate to = LocalDate.now().plusDays(70);
        var edificio = testData.edificio();

        // Unassigned: evento sin ninguna asignación.
        var scUnassigned = testData.materiaYComision();
        Occurrence unassignedOcc = seedOccurrence(scUnassigned, from.plusDays(1), 30);
        Long unassignedEventId = unassignedOcc.getEvent().getId();

        // Overcrowded: enrolled > capacity del aula asignada.
        var scOvercrowd = testData.materiaYComision();
        Classroom aulaChica = testData.aula(edificio, testData.tipoAulaNormal(), 1, 10, true);
        Occurrence overcrowdOcc = seedOccurrence(scOvercrowd, from.plusDays(2), 50);
        Long overcrowdEventId = overcrowdOcc.getEvent().getId();
        assignOk(overcrowdOcc.getId(), aulaChica.getId());

        // Overlap: dos eventos distintos, misma aula, mismo horario -> la segunda allocation se siembra por repo.
        var scOverlapA = testData.materiaYComision();
        var scOverlapB = testData.materiaYComision();
        Classroom aulaOverlap = testData.aula(edificio, testData.tipoAulaNormal(), 1, 100, true);
        LocalDate overlapDate = from.plusDays(3);
        Occurrence overlapOccA = seedOccurrence(scOverlapA, overlapDate, 20);
        Occurrence overlapOccB = seedOccurrence(scOverlapB, overlapDate, 20);
        assignOk(overlapOccA.getId(), aulaOverlap.getId());
        allocateDirect(overlapOccB, aulaOverlap.getId());
        Long overlapEventAId = overlapOccA.getEvent().getId();
        Long overlapEventBId = overlapOccB.getEvent().getId();

        AcademicEventResponseDto[] unassigned = getList("/v1/allocations/unassigned", from, to,
                AcademicEventResponseDto[].class);
        assertThat(unassigned).anySatisfy(e -> assertThat(e.id()).isEqualTo(unassignedEventId));

        OvercrowdedAllocationDto[] overcrowded = getList("/v1/allocations/overcrowded", from, to,
                OvercrowdedAllocationDto[].class);
        assertThat(overcrowded).anySatisfy(o -> {
            assertThat(o.event().id()).isEqualTo(overcrowdEventId);
            assertThat(o.classroom().id()).isEqualTo(aulaChica.getId());
            assertThat(o.enrolled()).isEqualTo(50);
            assertThat(o.capacity()).isEqualTo(10);
            assertThat(o.excess()).isEqualTo(40);
        });

        ClassroomOverlapDto[] overlaps = getList("/v1/allocations/overlaps", from, to,
                ClassroomOverlapDto[].class);
        assertThat(overlaps).anySatisfy(o -> {
            assertThat(o.classroom().id()).isEqualTo(aulaOverlap.getId());
            assertThat(Set.of(o.eventA().id(), o.eventB().id()))
                    .containsExactlyInAnyOrder(overlapEventAId, overlapEventBId);
        });
    }

    @Test
    @DisplayName("GET /v1/allocations/unassigned sin 'to' usa el fin del período académico activo con mayor endDate")
    void unassigned_withoutTo_usesActivePeriodEndDate() throws Exception {
        // Año deliberadamente muy lejano: garantiza ser el endDate máximo entre todos los períodos
        // activos existentes (los de otros tests usan años <= 2600), sin importar el orden de ejecución.
        int year = 9000 + (int) (IntegrationTestData.nextSeq() % 500);
        var period = academicPeriodService.findOrCreate(year, TermType.ANUAL).value();

        Long eventId = seedUniqueEventWithoutClassroom(period.endDate(), 20);

        mockMvc.perform(get("/v1/allocations/unassigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + eventId + ")]").exists());
    }

    @Test
    @DisplayName("GET /v1/allocations/overcrowded con to<from responde 400")
    void overcrowded_invalidRange_returns400() throws Exception {
        LocalDate from = LocalDate.now().plusDays(10);
        LocalDate to = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/v1/allocations/overcrowded")
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid date range"));
    }
}
