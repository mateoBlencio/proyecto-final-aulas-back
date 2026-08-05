package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración de los endpoints de historial de auditoría (Envers) contra Postgres real:
 * commits reales (sin {@code @Transactional}, Envers lo exige), flujo completo
 * crear evento → asignar → reasignar → consultar los 3 historiales.
 */
@Import(IntegrationTestData.class)
@DisplayName("Audit History API (integración)")
class AuditHistoryApiIntegrationTest extends AbstractIntegrationTest {

    private static final String USER = "integration-test@frc.utn.edu.ar";
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final int DURATION = 90;

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

    /**
     * Evento recurrente de 1 sola ocurrencia en {@code date}, creado por el servicio real.
     * Al invocar el servicio directamente (sin request HTTP) hay que poblar el
     * {@code SecurityContext} a mano para que la revisión de Envers registre el usuario.
     */
    private Occurrence seedOccurrence(LocalDate date) {
        var sc = testData.materiaYComision();
        var dto = new CreateRecurringEventRequestDto(
                30, START, DURATION, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        Long eventId;
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(USER, null, "ROLE_SUBSECRETARIA"));
        try {
            eventId = academicEventService.createRecurringEvent(dto).id();
        } finally {
            SecurityContextHolder.clearContext();
        }
        List<Occurrence> occurrences = occurrenceRepository.findByEvent_Id(eventId);
        assertThat(occurrences).hasSize(1);
        return occurrences.getFirst();
    }

    private long assignOk(Long occurrenceId, Integer classroomId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/allocations/occurrences/{id}", occurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(classroomId, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("GET /v1/allocations/occurrences/{id}/allocation-history refleja asignación, reasignación y baja")
    void allocationHistory_reflectsAssignReassignAndDelete() throws Exception {
        Classroom aulaA = testData.aula(testData.edificio());
        Classroom aulaB = testData.aula(testData.edificio());
        Occurrence occurrence = seedOccurrence(LocalDate.now().plusDays(21));

        long allocationId = assignOk(occurrence.getId(), aulaA.getId());
        mockMvc.perform(put("/v1/allocations/{id}", allocationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(aulaB.getId(), null))))
                .andExpect(status().isOk());
        // Baja directa por repositorio (no hay endpoint de delete): commit real, Envers registra DEL.
        allocationRepository.deleteById(allocationId);

        mockMvc.perform(get("/v1/allocations/occurrences/{id}/allocation-history", occurrence.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].kind").value("CREATED"))
                .andExpect(jsonPath("$[0].user").value(USER))
                .andExpect(jsonPath("$[0].snapshot.classroomId").value(aulaA.getId()))
                .andExpect(jsonPath("$[0].snapshot.occurrenceId").value(occurrence.getId()))
                .andExpect(jsonPath("$[0].snapshot.source").value("MANUAL"))
                .andExpect(jsonPath("$[1].kind").value("MODIFIED"))
                .andExpect(jsonPath("$[1].snapshot.classroomId").value(aulaB.getId()))
                .andExpect(jsonPath("$[2].kind").value("DELETED"))
                .andExpect(jsonPath("$[2].snapshot").doesNotExist());
    }

    @Test
    @DisplayName("Las revisiones vienen en orden ascendente y con fecha")
    void allocationHistory_revisionsAscendingWithDate() throws Exception {
        Classroom aulaA = testData.aula(testData.edificio());
        Classroom aulaB = testData.aula(testData.edificio());
        Occurrence occurrence = seedOccurrence(LocalDate.now().plusDays(22));

        long allocationId = assignOk(occurrence.getId(), aulaA.getId());
        mockMvc.perform(put("/v1/allocations/{id}", allocationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AllocateOccurrenceRequestDto(aulaB.getId(), null))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(
                        get("/v1/allocations/occurrences/{id}/allocation-history", occurrence.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").exists())
                .andReturn();

        var revisions = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(0).get("revision").asInt()).isLessThan(revisions.get(1).get("revision").asInt());
    }

    @Test
    @DisplayName("GET /v1/allocations/occurrences/{id}/history muestra el cambio de estado SCHEDULED → ASSIGNED")
    void occurrenceHistory_showsStatusTransition() throws Exception {
        Classroom aula = testData.aula(testData.edificio());
        Occurrence occurrence = seedOccurrence(LocalDate.now().plusDays(23));
        assignOk(occurrence.getId(), aula.getId());

        mockMvc.perform(get("/v1/allocations/occurrences/{id}/history", occurrence.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].kind").value("CREATED"))
                .andExpect(jsonPath("$[0].snapshot.status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].snapshot.eventId").value(occurrence.getEvent().getId()))
                .andExpect(jsonPath("$[1].kind").value("MODIFIED"))
                .andExpect(jsonPath("$[1].snapshot.status").value("ASSIGNED"))
                .andExpect(jsonPath("$[1].user").value(USER));
    }

    @Test
    @DisplayName("GET /v1/events/{id}/history devuelve el alta del evento con los campos del subtipo recurrente")
    void eventHistory_returnsCreationWithRecurringFields() throws Exception {
        Occurrence occurrence = seedOccurrence(LocalDate.now().plusDays(24));
        Long eventId = occurrence.getEvent().getId();

        mockMvc.perform(get("/v1/events/{id}/history", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].kind").value("CREATED"))
                .andExpect(jsonPath("$[0].user").value(USER))
                .andExpect(jsonPath("$[0].snapshot.type").value("RECURRING"))
                .andExpect(jsonPath("$[0].snapshot.enrolled").value(30))
                .andExpect(jsonPath("$[0].snapshot.durationMinutes").value(DURATION))
                .andExpect(jsonPath("$[0].snapshot.subjectId").exists())
                .andExpect(jsonPath("$[0].snapshot.commissionId").exists());
    }

    @Test
    @DisplayName("Historial de una ocurrencia existente sin asignaciones devuelve lista vacía")
    void allocationHistory_occurrenceWithoutAllocations_returnsEmptyList() throws Exception {
        Occurrence occurrence = seedOccurrence(LocalDate.now().plusDays(25));

        mockMvc.perform(get("/v1/allocations/occurrences/{id}/allocation-history", occurrence.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Historial de ids inexistentes responde 404 en los 3 endpoints")
    void history_nonExistentIds_return404() throws Exception {
        mockMvc.perform(get("/v1/events/{id}/history", 999_999_999L))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/allocations/occurrences/{id}/history", 999_999_999L))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/allocations/occurrences/{id}/allocation-history", 999_999_999L))
                .andExpect(status().isNotFound());
    }
}
