package ar.edu.utn.frc.siga.events;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.event.AcademicPeriodChanged;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The listener runs on another thread (@Async), without the publisher's operation context. The revisions
 * of the recalculated occurrences must share an {@code operacion_id} and the description.
 */
@Import(IntegrationTestData.class)
@DisplayName("AcademicPeriodChangedListener (integración): operación de auditoría en el hilo del listener")
class AcademicPeriodChangedListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicEventService academicEventService;
    @Autowired
    private CommissionService commissionService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Test
    @DisplayName("acortar el período borra ocurrencias futuras bajo una sola operación con la descripción de la tabla")
    void recalculatedOccurrencesShareOneOperation() {
        var sc = testData.materiaYComision();
        LocalDate start = LocalDate.now().plusDays(8);
        var dto = new CreateRecurringEventRequestDto(30, LocalTime.of(8, 0), 90, start.getDayOfWeek(),
                start, start.plusWeeks(5), sc.subjectId(), sc.commissionId());
        Long eventId = academicEventService.createRecurringEvent(dto).id();
        List<Long> occurrenceIds = occurrenceRepository.findByEvent_Id(eventId).stream().map(Occurrence::getId).toList();
        assertThat(occurrenceIds).hasSize(6);
        AcademicPeriodResponseDto period = commissionService.findByIds(List.of(sc.commissionId()))
                .getFirst().academicPeriod();

        // window ending on the day of the first occurrence: the other five are left out
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                eventPublisher.publishEvent(new AcademicPeriodChanged(1L, period.year(), period.semester(),
                        LocalDate.now().minusYears(5), start, null, null)));

        String sql = "SELECT r.descripcion AS descripcion, r.operacion_id AS operacion_id FROM revinfo r "
                + "JOIN ocurrencia_aud o ON o.rev = r.rev WHERE o.id_ocurrencia IN (:ids) AND o.revtype = 2";
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(namedJdbc.queryForList(sql, Map.of("ids", occurrenceIds))).hasSize(5));

        List<Map<String, Object>> deletions = namedJdbc.queryForList(sql, Map.of("ids", occurrenceIds));
        assertThat(deletions).extracting(row -> row.get("descripcion"))
                .containsOnly("Recálculo de ocurrencias por cambio de período académico");
        Set<Object> operations = deletions.stream().map(row -> row.get("operacion_id")).collect(Collectors.toSet());
        assertThat(operations).hasSize(1).doesNotContainNull();
    }
}
