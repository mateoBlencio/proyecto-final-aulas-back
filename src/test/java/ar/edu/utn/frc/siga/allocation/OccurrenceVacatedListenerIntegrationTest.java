package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocationItemRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceVacated;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The listener runs on another thread (@Async): the {@code AuditOperationContext} ThreadLocal of the
 * publisher thread does not reach it, so the revision that deletes the allocation only has an operation if the
 * annotation on {@code on} itself is applied through the proxy.
 */
@Import(IntegrationTestData.class)
@DisplayName("OccurrenceVacatedListener (integración): operación de auditoría en el hilo del listener")
class OccurrenceVacatedListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("la revisión que borra la asignación lleva la descripción y un operacion_id")
    void deallocationRevisionCarriesOperation() throws Exception {
        var sc = testData.materiaYComision();
        Classroom classroom = testData.aula(testData.edificio());
        LocalDate date = LocalDate.now().plusDays(21);
        var dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, date.getDayOfWeek(), date, date, sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        long allocationId = allocate(occurrence.getId(), classroom.getId());

        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                eventPublisher.publishEvent(new OccurrenceVacated(occurrence.getId())));

        String sql = "SELECT r.descripcion AS descripcion, r.operacion_id AS operacion_id FROM revinfo r "
                + "JOIN asignacion_aula_aud a ON a.rev = r.rev WHERE a.id_asignacion = ? AND a.revtype = 2";
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForList(sql, allocationId)).hasSize(1));

        Map<String, Object> deletion = jdbcTemplate.queryForList(sql, allocationId).getFirst();
        assertThat(deletion.get("descripcion")).isEqualTo("Liberación de aula por ocurrencia desocupada");
        assertThat(deletion.get("operacion_id")).isNotNull();
    }

    private long allocate(Long occurrenceId, Long classroomId) throws Exception {
        var body = new AllocationBatchRequestDto(
                List.of(new AllocationItemRequestDto(List.of(occurrenceId), null, null, null, classroomId)), null);
        MvcResult result = mockMvc.perform(post("/v1/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(0).get("id").asLong();
    }
}
