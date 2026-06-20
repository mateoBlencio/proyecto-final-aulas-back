package ar.edu.utn.frc.classroom_allocation.solver.model;

import ar.edu.utn.frc.classroom_allocation.allocation.model.Occurrence;
import ar.edu.utn.frc.classroom_allocation.allocation.model.UniqueEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueEventTest {

    @Test
    void upUe001_occurrences_singleDate() {
        UniqueEvent event = UniqueEvent.builder()
                .planningId("e1").enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .date(LocalDate.of(2024, 7, 23))
                .build();

        List<Occurrence> occ = event.toOccurrences();
        assertThat(occ).extracting(Occurrence::getDate)
                .containsExactly(LocalDate.of(2024, 7, 23));
    }

    @Test
    void upUe002_endTime_90min() {
        UniqueEvent event = UniqueEvent.builder()
                .planningId("e1").enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .date(LocalDate.of(2024, 7, 23))
                .build();

        assertThat(event.endTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void upUe003_endTime_lateNight() {
        UniqueEvent event = UniqueEvent.builder()
                .planningId("e1").enrolled(30)
                .startTime(LocalTime.of(21, 35))
                .duration(Duration.ofMinutes(90))
                .date(LocalDate.of(2024, 7, 23))
                .build();

        assertThat(event.endTime()).isEqualTo(LocalTime.of(23, 5));
    }
}
