package ar.edu.utn.frc.siga.events.model;

import ar.edu.utn.frc.siga.events.EventTestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Occurrence.isPast / startTime / endTime")
class OccurrenceTest {

    @Test
    @DisplayName("fecha de ayer → pasado")
    void fechaDeAyerEsPasado() {
        Occurrence occurrence = occurrence(LocalDate.now().minusDays(1), LocalTime.NOON, Duration.ofMinutes(60));

        assertThat(occurrence.isPast()).isTrue();
    }

    @Test
    @DisplayName("fecha de mañana → no pasado")
    void fechaDeMananaNoEsPasado() {
        Occurrence occurrence = occurrence(LocalDate.now().plusDays(1), LocalTime.NOON, Duration.ofMinutes(60));

        assertThat(occurrence.isPast()).isFalse();
    }

    /**
     * Se parte de LocalDateTime.now() y se separa en fecha/hora recién al construir la
     * ocurrencia: si "ahora - 2h" cruza medianoche, la fecha resultante ya es la de ayer,
     * así que el caso sigue siendo válido sin necesidad de ramificar según la hora del día.
     */
    @Test
    @DisplayName("startTime 2 horas antes de ahora (mismo día, o ayer si cruza medianoche) → pasado")
    void horarioDosHorasAntesEsPasado() {
        LocalDateTime past = LocalDateTime.now().minusHours(2);
        Occurrence occurrence = occurrence(past.toLocalDate(), past.toLocalTime(), Duration.ofMinutes(60));

        assertThat(occurrence.isPast()).isTrue();
    }

    @Test
    @DisplayName("startTime 2 horas después de ahora (mismo día, o mañana si cruza medianoche) → no pasado")
    void horarioDosHorasDespuesNoEsPasado() {
        LocalDateTime future = LocalDateTime.now().plusHours(2);
        Occurrence occurrence = occurrence(future.toLocalDate(), future.toLocalTime(), Duration.ofMinutes(60));

        assertThat(occurrence.isPast()).isFalse();
    }

    @Test
    @DisplayName("startTime() delega en el startTime del evento")
    void startTimeDelegaEnElEvento() {
        LocalTime startTime = LocalTime.of(9, 15);
        Occurrence occurrence = occurrence(LocalDate.now(), startTime, Duration.ofMinutes(90));

        assertThat(occurrence.startTime()).isEqualTo(startTime);
    }

    @Test
    @DisplayName("endTime() = startTime + duration del evento")
    void endTimeEsStartTimeMasDuracion() {
        LocalTime startTime = LocalTime.of(9, 15);
        Duration duration = Duration.ofMinutes(90);
        Occurrence occurrence = occurrence(LocalDate.now(), startTime, duration);

        assertThat(occurrence.endTime()).isEqualTo(startTime.plus(duration));
    }

    private Occurrence occurrence(LocalDate date, LocalTime startTime, Duration duration) {
        UniqueEvent event = EventTestData.uniqueEvent(1L, date, startTime, duration);
        return EventTestData.occurrence(10L, event, date, OccurrenceStatus.SCHEDULED);
    }
}
