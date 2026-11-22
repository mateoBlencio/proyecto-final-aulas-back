package ar.edu.utn.frc.siga.events;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Las consultas que deciden qué eventos recurrentes cuentan miran {@code habilitado_siga}, no
 * {@code habilitado_sysacad}: un evento dado de baja por SIGA queda afuera aunque SysAcad lo tenga habilitado.
 */
@Import(IntegrationTestData.class)
@DisplayName("Filtro por habilitado_siga en consultas de eventos recurrentes (integración)")
class RecurringEventSigaEnabledIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger WINDOWS = new AtomicInteger();

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RecurringEventRepository recurringEventRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private JdbcTemplate jdbc;

    /** Evento de un solo día creado por el servicio (nace con habilitado_siga=true y hash nulo, como uno manual). */
    private Long createEvent(IntegrationTestData.SubjectAndCommission sc, LocalDate date, LocalTime start) {
        return testData.eventoRecurrente(sc.subjectId(), sc.commissionId(), date.getDayOfWeek(), start, 60,
                date, date, 20);
    }

    private void setFlags(Long eventId, String hashRaw, boolean sysacadEnabled, boolean sigaEnabled) {
        int updated = jdbc.update("UPDATE evento_academico SET hash_raw = ?, habilitado_sysacad = ?, habilitado_siga = ? "
                + "WHERE id_evento_academico = ?", hashRaw, sysacadEnabled, sigaEnabled, eventId);
        assertThat(updated).isEqualTo(1);
    }

    @Test
    @DisplayName("findActiveBySubjectAndCommission y findActiveBySubject excluyen habilitado_siga=false (aunque habilitado_sysacad sea true o el evento sea manual) e incluyen el manual habilitado")
    void activeQueriesExcludeEventsDisabledBySiga() {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(14);
        Long syncDownBySiga = createEvent(sc, date, LocalTime.of(8, 0));
        Long syncEnabled = createEvent(sc, date, LocalTime.of(9, 0));
        Long manualEnabled = createEvent(sc, date, LocalTime.of(10, 0));
        Long manualDownBySiga = createEvent(sc, date, LocalTime.of(11, 0));
        setFlags(syncDownBySiga, "h1", true, false);
        setFlags(syncEnabled, "h2", true, true);
        setFlags(manualDownBySiga, null, false, false);

        List<Long> bySubjectAndCommission = ids(recurringEventRepository
                .findActiveBySubjectAndCommission(sc.subjectId(), sc.commissionId(), LocalDate.now()));
        List<Long> bySubject = ids(recurringEventRepository.findActiveBySubject(sc.subjectId(), LocalDate.now()));

        assertThat(bySubjectAndCommission).containsExactlyInAnyOrder(syncEnabled, manualEnabled);
        assertThat(bySubject).containsExactlyInAnyOrder(syncEnabled, manualEnabled);
    }

    @Test
    @DisplayName("findBySigaEnabledTrue excluye habilitado_siga=false aunque habilitado_sysacad sea true, e incluye el evento manual")
    void findBySigaEnabledTrueExcludesDisabledBySiga() {
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        LocalDate date = LocalDate.now().plusDays(14);
        Long syncDownBySiga = createEvent(sc, date, LocalTime.of(8, 0));
        Long syncEnabled = createEvent(sc, date, LocalTime.of(9, 0));
        Long manualEnabled = createEvent(sc, date, LocalTime.of(10, 0));
        setFlags(syncDownBySiga, "h1", true, false);
        setFlags(syncEnabled, "h2", false, true);

        List<Long> enabled = ids(recurringEventRepository.findBySigaEnabledTrue());

        assertThat(enabled).contains(syncEnabled, manualEnabled).doesNotContain(syncDownBySiga);
    }

    @Test
    @DisplayName("findEventIdsWithUnallocatedOccurrencesBetween excluye las ocurrencias NEEDS_ROOM de un evento con habilitado_siga=false aunque tenga habilitado_sysacad=true")
    void unallocatedCandidatesExcludeEventsDisabledBySiga() {
        LocalDate from = LocalDate.now().plusYears(4).plusDays(10L * WINDOWS.getAndIncrement());
        LocalDate to = from.plusDays(5);
        IntegrationTestData.SubjectAndCommission sc = testData.materiaYComision();
        Long enabledEvent = createEvent(sc, from.plusDays(1), LocalTime.of(9, 0));
        Long disabledEvent = createEvent(sc, from.plusDays(2), LocalTime.of(9, 0));
        setFlags(disabledEvent, "h", true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ocurrencia WHERE id_evento_academico = ? AND estado = 'NEEDS_ROOM'",
                Integer.class, disabledEvent)).as("la ocurrencia de seed existe y espera aula").isEqualTo(1);

        List<Long> eventIds = allocationRepository.findEventIdsWithUnallocatedOccurrencesBetween(from, to);

        assertThat(eventIds).contains(enabledEvent).doesNotContain(disabledEvent);
    }

    private static List<Long> ids(List<RecurringEvent> events) {
        return events.stream().map(RecurringEvent::getId).toList();
    }
}
