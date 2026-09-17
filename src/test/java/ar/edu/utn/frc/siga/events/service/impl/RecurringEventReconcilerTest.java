package ar.edu.utn.frc.siga.events.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.common.service.OccurrenceAllocationQuery;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.OccurrenceWindow;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecurringEventReconciler.reconcileFuture")
class RecurringEventReconcilerTest {

    @Mock
    private OccurrenceRepository occurrenceRepository;
    @Mock
    private OccurrenceAllocationQuery occurrenceAllocationQuery;

    @InjectMocks
    private RecurringEventReconciler reconciler;

    @Test
    @DisplayName("crea las fechas deseadas que faltan y borra las sobrantes futuras no asignadas; conserva las asignadas")
    void reconcilesFutureOccurrences() {
        LocalDate nextMonday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
        RecurringEvent event = event(nextMonday, nextMonday.plusWeeks(3));

        LocalDate surplusAllocated = nextMonday.plusWeeks(10);
        LocalDate surplusFree = nextMonday.plusWeeks(11);
        Occurrence keep = occurrence(1L, event, nextMonday);
        Occurrence allocatedSurplus = occurrence(2L, event, surplusAllocated);
        Occurrence freeSurplus = occurrence(3L, event, surplusFree);
        when(occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(eq(event.getId()), any()))
                .thenReturn(List.of(keep, allocatedSurplus, freeSurplus));
        when(occurrenceAllocationQuery.allocatedAmong(any())).thenReturn(Set.of(2L));

        reconciler.reconcileFuture(event, new OccurrenceWindow(nextMonday, nextMonday.plusWeeks(3), null, null));

        ArgumentCaptor<List<Occurrence>> deleted = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).extracting(Occurrence::getId).containsExactly(3L);

        ArgumentCaptor<List<Occurrence>> created = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(created.capture());
        assertThat(created.getValue()).extracting(Occurrence::getDate)
                .containsExactlyInAnyOrder(nextMonday.plusWeeks(1), nextMonday.plusWeeks(2), nextMonday.plusWeeks(3));
    }

    private RecurringEvent event(LocalDate startDate, LocalDate endDate) {
        return RecurringEvent.builder()
                .id(7L).enrolled(30).startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(90))
                .dayOfWeek(DayOfWeek.MONDAY).startDate(startDate).endDate(endDate)
                .subjectId(1L).commissionId(1L).build();
    }

    private Occurrence occurrence(Long id, RecurringEvent event, LocalDate date) {
        return Occurrence.builder().id(id).event(event).date(date).status(OccurrenceStatus.NEEDS_ROOM).build();
    }
}
