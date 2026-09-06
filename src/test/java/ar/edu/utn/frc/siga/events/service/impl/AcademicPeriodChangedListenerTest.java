package ar.edu.utn.frc.siga.events.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.event.AcademicPeriodChanged;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.OccurrenceWindow;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicPeriodChangedListener")
class AcademicPeriodChangedListenerTest {

    @Mock
    private RecurringEventRepository recurringEventRepository;
    @Mock
    private CommissionService commissionService;
    @Mock
    private RecurringEventReconciler reconciler;

    @InjectMocks
    private AcademicPeriodChangedListener listener;

    @Test
    @DisplayName("recalcula sólo los eventos cuya comisión pertenece al período que cambió")
    void reconcilesOnlyMatchingPeriod() {
        RecurringEvent matching = event(1L, 10L);
        RecurringEvent otherPeriod = event(2L, 20L);
        when(recurringEventRepository.findAll()).thenReturn(List.of(matching, otherPeriod));
        when(commissionService.findByIds(any())).thenReturn(List.of(
                commission(10L, 2026, 0),
                commission(20L, 2026, 1)));

        listener.on(new AcademicPeriodChanged(1L, 2026, 0,
                LocalDate.of(2026, 3, 16), LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 27)));

        verify(reconciler).reconcileFuture(eq(matching), any(OccurrenceWindow.class));
        verify(reconciler, never()).reconcileFuture(eq(otherPeriod), any());
    }

    private RecurringEvent event(Long id, Long commissionId) {
        return RecurringEvent.builder()
                .id(id).enrolled(30).startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(90))
                .dayOfWeek(DayOfWeek.MONDAY).startDate(LocalDate.of(2026, 3, 16))
                .subjectId(1L).commissionId(commissionId).build();
    }

    private CommissionResponseDto commission(Long id, int year, int semester) {
        return new CommissionResponseDto(id, "C-" + id,
                new AcademicPeriodResponseDto(year, semester, LocalDate.of(year, 3, 16), LocalDate.of(year, 11, 30)));
    }
}
