package PF.classroom_allocation.solver.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringEventTest {

    private RecurringEvent recurring(DayOfWeek dow, LocalDate start, LocalDate end) {
        return RecurringEvent.builder()
                .id("e1").enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(dow)
                .startDate(start).endDate(end)
                .subject("X").section("1C1")
                .build();
    }

    @Test
    void upRe001_occurrences_oneWeek() {
        // 2024-03-04 (Mon) to 2024-03-10 (Sun) — 1 Monday
        RecurringEvent ev = recurring(DayOfWeek.MONDAY,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 10));
        assertThat(ev.occurrences()).containsExactly(LocalDate.of(2024, 3, 4));
    }

    @Test
    void upRe002_occurrences_twoWeeks() {
        RecurringEvent ev = recurring(DayOfWeek.MONDAY,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 17));
        assertThat(ev.occurrences()).containsExactly(
                LocalDate.of(2024, 3, 4),
                LocalDate.of(2024, 3, 11));
    }

    @Test
    void upRe003_occurrences_dayNotInRange() {
        // Mon-Thu range, looking for Friday → empty
        RecurringEvent ev = recurring(DayOfWeek.FRIDAY,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 7));
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test
    void upRe004_occurrences_startEqualsEnd_coincides() {
        RecurringEvent ev = recurring(DayOfWeek.MONDAY,
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 4));
        assertThat(ev.occurrences()).containsExactly(LocalDate.of(2024, 3, 4));
    }

    @Test
    void upRe005_occurrences_fullSemester() {
        // Thursdays from 2024-03-07 to 2024-06-27
        RecurringEvent ev = recurring(DayOfWeek.THURSDAY,
                LocalDate.of(2024, 3, 7), LocalDate.of(2024, 6, 27));
        assertThat(ev.occurrences()).hasSize(17);
    }

    @Test
    void upRe006_endTime() {
        RecurringEvent ev = RecurringEvent.builder()
                .id("e1").enrolled(30)
                .startTime(LocalTime.of(18, 15))
                .duration(Duration.ofMinutes(135))
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.of(2024, 3, 4))
                .endDate(LocalDate.of(2024, 6, 27))
                .subject("X").section("1C1")
                .build();
        assertThat(ev.endTime()).isEqualTo(LocalTime.of(20, 30));
    }
}
