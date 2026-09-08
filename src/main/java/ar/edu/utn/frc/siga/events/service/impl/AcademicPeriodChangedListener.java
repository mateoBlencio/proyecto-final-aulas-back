package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.event.AcademicPeriodChanged;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.events.model.OccurrenceWindow;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Al cambiar un período académico (nuevo receso, fin distinto, corte de cuatrimestre), recalcula las
 * ocurrencias futuras no asignadas de los eventos recurrentes cuya comisión pertenece a ese período.
 * At-least-once por el log de eventos de Modulith; el recálculo es idempotente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AcademicPeriodChangedListener {

    private final RecurringEventRepository recurringEventRepository;
    private final CommissionService commissionService;
    private final RecurringEventReconciler reconciler;

    @ApplicationModuleListener
    void on(AcademicPeriodChanged event) {
        List<RecurringEvent> allEvents = recurringEventRepository.findAll();
        Set<Long> commissionIds = allEvents.stream()
                .map(RecurringEvent::getCommissionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (commissionIds.isEmpty()) {
            return;
        }

        Map<Long, CommissionResponseDto> commissionsById = commissionService.findByIds(commissionIds).stream()
                .collect(Collectors.toMap(CommissionResponseDto::id, commission -> commission, (first, ignored) -> first));

        List<RecurringEvent> affected = allEvents.stream()
                .filter(recurring -> belongsToPeriod(commissionsById.get(recurring.getCommissionId()), event))
                .toList();

        log.info("AcademicPeriodChanged year={}, semester={}: {} eventos recurrentes a recalcular",
                event.year(), event.semester(), affected.size());

        OccurrenceWindow window = new OccurrenceWindow(
                event.startDate(), event.endDate(), event.recessStart(), event.recessEnd());
        affected.forEach(recurring -> reconciler.reconcileFuture(recurring, window));
    }

    private static boolean belongsToPeriod(CommissionResponseDto commission, AcademicPeriodChanged event) {
        if (commission == null || commission.academicPeriod() == null) {
            return false;
        }
        AcademicPeriodResponseDto period = commission.academicPeriod();
        return Objects.equals(period.year(), event.year()) && Objects.equals(period.semester(), event.semester());
    }
}
