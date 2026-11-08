package ar.edu.utn.frc.siga.events;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestData.class)
@DisplayName("OccurrenceService.findNextSlotsByEvents (integración, SQL nativo contra Postgres)")
class OccurrenceNextSlotsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OccurrenceService occurrenceService;
    @Autowired
    private IntegrationTestData testData;

    private SubjectAndCommission academic;

    @BeforeEach
    void setUp() {
        academic = testData.materiaYComision();
    }

    private LocalDate firstMonday() {
        return LocalDate.now().plusYears(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 400);
    }

    private Long weeklyEvent(LocalDate first, int weeks) {
        return testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(8, 0), 60, first, first.plusWeeks(weeks - 1), 30);
    }

    @Test
    @DisplayName("Devuelve como máximo limit clases por evento y son las primeras por fecha")
    void returnsFirstLimitPerEvent() {
        LocalDate first = firstMonday();
        Long eventId = weeklyEvent(first, 5);

        List<OccurrenceSlotDto> slots = occurrenceService.findNextSlotsByEvents(Set.of(eventId), first, List.of(), 2);

        assertThat(slots).extracting(OccurrenceSlotDto::date).containsExactlyInAnyOrder(first, first.plusWeeks(1));
    }

    @Test
    @DisplayName("Excluye los espejos aunque caigan en la misma fecha")
    void excludesMirrors() {
        LocalDate first = firstMonday();
        Long eventId = weeklyEvent(first, 3);
        OccurrenceSlotDto principal = occurrenceService.findSlotsByEvent(eventId, first).stream()
                .filter(s -> s.date().equals(first)).findFirst().orElseThrow();
        occurrenceService.createSimultaneous(principal.occurrenceId(), 2);

        List<OccurrenceSlotDto> slots = occurrenceService.findNextSlotsByEvents(Set.of(eventId), first, List.of(), 2);

        assertThat(slots).extracting(OccurrenceSlotDto::mirrorOfOccurrenceId).containsOnlyNulls();
        assertThat(slots).extracting(OccurrenceSlotDto::date).containsExactlyInAnyOrder(first, first.plusWeeks(1));
    }

    @Test
    @DisplayName("Saltea las fechas excluidas y toma la siguiente")
    void skipsExcludedDates() {
        LocalDate first = firstMonday();
        Long eventId = weeklyEvent(first, 5);

        List<OccurrenceSlotDto> slots = occurrenceService.findNextSlotsByEvents(Set.of(eventId), first,
                List.of(first, first.plusWeeks(2)), 2);

        assertThat(slots).extracting(OccurrenceSlotDto::date)
                .containsExactlyInAnyOrder(first.plusWeeks(1), first.plusWeeks(3));
    }

    @Test
    @DisplayName("Ignora las clases anteriores a from e incluye la fecha from misma")
    void ignoresClassesBeforeFrom() {
        LocalDate first = firstMonday();
        Long eventId = weeklyEvent(first, 5);

        List<OccurrenceSlotDto> slots = occurrenceService.findNextSlotsByEvents(Set.of(eventId),
                first.plusWeeks(2), List.of(), 5);

        assertThat(slots).extracting(OccurrenceSlotDto::date)
                .containsExactlyInAnyOrder(first.plusWeeks(2), first.plusWeeks(3), first.plusWeeks(4));
    }

    @Test
    @DisplayName("Aplica el límite por separado a cada evento")
    void limitsEachEventSeparately() {
        LocalDate first = firstMonday();
        Long eventA = weeklyEvent(first, 4);
        Long eventB = weeklyEvent(first.plusWeeks(1), 4);

        List<OccurrenceSlotDto> slots = occurrenceService.findNextSlotsByEvents(Set.of(eventA, eventB), first,
                List.of(), 2);

        assertThat(slots).filteredOn(s -> s.eventId().equals(eventA)).extracting(OccurrenceSlotDto::date)
                .containsExactlyInAnyOrder(first, first.plusWeeks(1));
        assertThat(slots).filteredOn(s -> s.eventId().equals(eventB)).extracting(OccurrenceSlotDto::date)
                .containsExactlyInAnyOrder(first.plusWeeks(1), first.plusWeeks(2));
    }

    @Test
    @DisplayName("Sin eventos devuelve lista vacía")
    void emptyEventIdsReturnsEmpty() {
        assertThat(occurrenceService.findNextSlotsByEvents(Set.of(), LocalDate.now(), List.of(), 2)).isEmpty();
    }

    @Test
    @DisplayName("Si todas las clases caen en fechas excluidas devuelve lista vacía")
    void allDatesExcludedReturnsEmpty() {
        LocalDate first = firstMonday();
        Long eventId = weeklyEvent(first, 2);

        assertThat(occurrenceService.findNextSlotsByEvents(Set.of(eventId), first,
                List.of(first, first.plusWeeks(1)), 2)).isEmpty();
    }
}
