package ar.edu.utn.frc.classroom_allocation.solver.model;

import ar.edu.utn.frc.classroom_allocation.allocation.model.Occurrence;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringEventTest {

    private RecurringEvent recurring(DayOfWeek dow, LocalDate start, LocalDate end) {
        return RecurringEvent.builder()
                .planningId("e1").enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(dow)
                .startDate(start).endDate(end)
                .build();
    }

    @Test
    void upRe001_occurrences_oneWeek() {
        RecurringEvent ev = recurring(DayOfWeek.MONDAY,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 10));
        assertThat(ev.toOccurrences()).extracting(Occurrence::getDate)
                .containsExactly(LocalDate.of(2024, 3, 4));
    }

    @Test
    void upRe002_occurrences_twoWeeks() {
        RecurringEvent ev = recurring(DayOfWeek.MONDAY,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 17));
        assertThat(ev.toOccurrences()).extracting(Occurrence::getDate)
                .containsExactly(
                        LocalDate.of(2024, 3, 4),
                        LocalDate.of(2024, 3, 11));
    }

    @Test
    void upRe003_occurrences_dayNotInRange() {
        RecurringEvent ev = recurring(DayOfWeek.FRIDAY,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 7));
        assertThat(ev.toOccurrences()).isEmpty();
    }

    @Test
    void upRe004_occurrences_startEqualsEnd_coincides() {
        RecurringEvent ev = recurring(DayOfWeek.MONDAY,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 4));
        assertThat(ev.toOccurrences()).extracting(Occurrence::getDate)
                .containsExactly(LocalDate.of(2024, 3, 4));
    }

    @Test
    void upRe005_occurrences_fullSemester() {
        RecurringEvent ev = recurring(DayOfWeek.THURSDAY,
                LocalDate.of(2024, 3, 7), LocalDate.of(2024, 6, 27));
        assertThat(ev.toOccurrences()).hasSize(17);
    }

    @Test
    void upRe006_endTime() {
        RecurringEvent ev = RecurringEvent.builder()
                .planningId("e1").enrolled(30)
                .startTime(LocalTime.of(18, 15))
                .duration(Duration.ofMinutes(135))
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.of(2024, 3, 4))
                .endDate(LocalDate.of(2024, 6, 27))
                .build();
        assertThat(ev.endTime()).isEqualTo(LocalTime.of(20, 30));
    }
}
