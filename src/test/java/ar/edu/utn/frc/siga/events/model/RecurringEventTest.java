package ar.edu.utn.frc.siga.events.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecurringEvent.toOccurrences")
class RecurringEventTest {

    @Test
    @DisplayName("startDate cae el propio dayOfWeek: la primera ocurrencia es startDate")
    void primeraOcurrenciaMismoDia() {
        LocalDate startDate = LocalDate.of(2026, 1, 5);
        RecurringEvent event = event(startDate.getDayOfWeek(), startDate, startDate.plusWeeks(3));

        List<Occurrence> occurrences = event.toOccurrences();

        assertThat(occurrences.getFirst().getDate()).isEqualTo(startDate);
    }

    @Test
    @DisplayName("startDate cae otro día: la primera ocurrencia es nextOrSame(dayOfWeek)")
    void primeraOcurrenciaOtroDia() {
        LocalDate startDate = LocalDate.of(2026, 1, 5);
        DayOfWeek otherDay = startDate.getDayOfWeek().plus(2);
        RecurringEvent event = event(otherDay, startDate, startDate.plusWeeks(4));

        List<Occurrence> occurrences = event.toOccurrences();

        LocalDate expectedFirst = startDate.with(TemporalAdjusters.nextOrSame(otherDay));
        assertThat(occurrences.getFirst().getDate()).isEqualTo(expectedFirst).isAfter(startDate);
    }

    @Test
    @DisplayName("las ocurrencias avanzan de a 7 días")
    void pasoSemanal() {
        LocalDate startDate = LocalDate.of(2026, 1, 5);
        RecurringEvent event = event(startDate.getDayOfWeek(), startDate, startDate.plusWeeks(3));

        List<Occurrence> occurrences = event.toOccurrences();

        assertThat(occurrences).hasSize(4);
        for (int i = 1; i < occurrences.size(); i++) {
            assertThat(occurrences.get(i).getDate()).isEqualTo(occurrences.get(i - 1).getDate().plusDays(7));
        }
    }

    @Test
    @DisplayName("endDate es inclusive: si la última fecha cae justo en endDate, se genera")
    void endDateInclusive() {
        LocalDate startDate = LocalDate.of(2026, 1, 5);
        LocalDate endDate = startDate.plusWeeks(3);
        RecurringEvent event = event(startDate.getDayOfWeek(), startDate, endDate);

        List<Occurrence> occurrences = event.toOccurrences();

        assertThat(occurrences.getLast().getDate()).isEqualTo(endDate);
    }

    @Test
    @DisplayName("endDate null: genera hasta startDate + 1 año")
    void endDateNull() {
        LocalDate startDate = LocalDate.of(2026, 1, 5);
        RecurringEvent event = event(startDate.getDayOfWeek(), startDate, null);

        List<Occurrence> occurrences = event.toOccurrences();

        LocalDate limit = startDate.plusYears(1);
        LocalDate last = occurrences.getLast().getDate();
        assertThat(last).isBeforeOrEqualTo(limit);
        assertThat(last.plusDays(7)).isAfter(limit);
    }

    @Test
    @DisplayName("todas las ocurrencias generadas nacen SCHEDULED y referencian al evento")
    void naceScheduledYReferenciaAlEvento() {
        LocalDate startDate = LocalDate.of(2026, 1, 5);
        RecurringEvent event = event(startDate.getDayOfWeek(), startDate, startDate.plusWeeks(2));

        List<Occurrence> occurrences = event.toOccurrences();

        assertThat(occurrences).allSatisfy(o -> {
            assertThat(o.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
            assertThat(o.getEvent()).isSameAs(event);
        });
    }

    @Test
    @DisplayName("rango de 1 día: startDate == endDate cayendo el dayOfWeek → 1 ocurrencia")
    void rangoUnDiaCayendoElDia() {
        LocalDate startDate = LocalDate.of(2026, 1, 5);
        RecurringEvent event = event(startDate.getDayOfWeek(), startDate, startDate);

        List<Occurrence> occurrences = event.toOccurrences();

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().getDate()).isEqualTo(startDate);
    }

    @Test
    @DisplayName("rango de 1 día: startDate == endDate sin caer el dayOfWeek → 0 ocurrencias")
    void rangoUnDiaSinCaerElDia() {
        LocalDate startDate = LocalDate.of(2026, 1, 5);
        DayOfWeek otherDay = startDate.getDayOfWeek().plus(1);
        RecurringEvent event = event(otherDay, startDate, startDate);

        List<Occurrence> occurrences = event.toOccurrences();

        assertThat(occurrences).isEmpty();
    }

    @Test
    @DisplayName("toOccurrences(window): recorta al rango del período (clamp) por ambos bordes")
    void windowClamp() {
        LocalDate startDate = LocalDate.of(2026, 3, 2);
        RecurringEvent event = event(DayOfWeek.MONDAY, startDate, LocalDate.of(2026, 12, 21));
        OccurrenceWindow window = new OccurrenceWindow(
                LocalDate.of(2026, 3, 16), LocalDate.of(2026, 11, 30), null, null);

        List<Occurrence> occurrences = event.toOccurrences(window);

        assertThat(occurrences.getFirst().getDate()).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(occurrences.getLast().getDate()).isBeforeOrEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("toOccurrences(window): salta las fechas dentro del receso, bordes inclusive")
    void windowSkipsRecess() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        RecurringEvent event = event(DayOfWeek.MONDAY, startDate, LocalDate.of(2026, 8, 31));
        OccurrenceWindow window = new OccurrenceWindow(startDate, LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 27));

        List<LocalDate> dates = event.toOccurrences(window).stream().map(Occurrence::getDate).toList();

        assertThat(dates).contains(LocalDate.of(2026, 7, 6).minusWeeks(1))
                .doesNotContain(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 13),
                        LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27))
                .contains(LocalDate.of(2026, 8, 3));
    }

    private RecurringEvent event(DayOfWeek dayOfWeek, LocalDate startDate, LocalDate endDate) {
        return RecurringEvent.builder()
                .id(1L)
                .enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(dayOfWeek)
                .startDate(startDate)
                .endDate(endDate)
                .subjectId(1L)
                .commissionId(1L)
                .build();
    }
}
