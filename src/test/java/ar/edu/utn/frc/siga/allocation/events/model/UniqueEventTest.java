package ar.edu.utn.frc.siga.allocation.events.model;

import ar.edu.utn.frc.siga.allocation.AllocationTestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UniqueEvent.toOccurrences")
class UniqueEventTest {

    @Test
    @DisplayName("genera exactamente 1 ocurrencia en su date, SCHEDULED")
    void generaUnaOcurrenciaScheduled() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        UniqueEvent event = AllocationTestData.uniqueEvent(1L, date, LocalTime.of(10, 0), Duration.ofMinutes(60));

        List<Occurrence> occurrences = event.toOccurrences();

        assertThat(occurrences).hasSize(1);
        Occurrence occurrence = occurrences.getFirst();
        assertThat(occurrence.getDate()).isEqualTo(date);
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED);
        assertThat(occurrence.getEvent()).isSameAs(event);
    }

    @Test
    @DisplayName("la ocurrencia generada expone el startTime/endTime del evento")
    void exponeStartTimeYEndTime() {
        LocalTime startTime = LocalTime.of(14, 30);
        Duration duration = Duration.ofMinutes(45);
        UniqueEvent event = AllocationTestData.uniqueEvent(1L, LocalDate.of(2026, 3, 10), startTime, duration);

        Occurrence occurrence = event.toOccurrences().getFirst();

        assertThat(occurrence.startTime()).isEqualTo(startTime);
        assertThat(occurrence.endTime()).isEqualTo(startTime.plus(duration));
    }

    @Test
    @DisplayName("getType() devuelve UNIQUE_EVENT")
    void getTypeDevuelveUniqueEvent() {
        UniqueEvent event = AllocationTestData.uniqueEvent(1L, LocalDate.of(2026, 3, 10),
                LocalTime.of(9, 0), Duration.ofMinutes(30));

        assertThat(event.getType()).isEqualTo(EventType.UNIQUE_EVENT);
    }
}
