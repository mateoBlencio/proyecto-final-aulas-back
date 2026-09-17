package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.common.service.OccurrenceAllocationQuery;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.OccurrenceWindow;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconcilia las ocurrencias de un evento recurrente contra la ventana efectiva del período: agrega
 * las fechas deseadas que faltan y borra las sobrantes, tocando sólo ocurrencias futuras y no
 * asignadas. Idempotente: reconciliar dos veces con la misma ventana no cambia nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RecurringEventReconciler {

    private final OccurrenceRepository occurrenceRepository;
    private final OccurrenceAllocationQuery occurrenceAllocationQuery;

    @Transactional
    void reconcileFuture(RecurringEvent event, OccurrenceWindow window) {
        LocalDate today = LocalDate.now();
        Set<LocalDate> desiredFuture = event.toOccurrences(window).stream()
                .map(Occurrence::getDate)
                .filter(date -> date.isAfter(today))
                .collect(Collectors.toSet());

        List<Occurrence> existingFuture =
                occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(event.getId(), today.plusDays(1));
        Map<LocalDate, Occurrence> existingByDate = existingFuture.stream()
                .collect(Collectors.toMap(Occurrence::getDate, occurrence -> occurrence, (first, ignored) -> first));

        List<Occurrence> surplus = existingFuture.stream()
                .filter(occurrence -> !desiredFuture.contains(occurrence.getDate()))
                .toList();
        Set<Long> allocated = occurrenceAllocationQuery.allocatedAmong(
                surplus.stream().map(Occurrence::getId).toList());
        List<Occurrence> deletable = surplus.stream()
                .filter(occurrence -> !allocated.contains(occurrence.getId()))
                .toList();
        occurrenceRepository.deleteAll(deletable);

        List<Occurrence> missing = desiredFuture.stream()
                .filter(date -> !existingByDate.containsKey(date))
                .map(date -> Occurrence.builder()
                        .event(event)
                        .date(date)
                        .status(OccurrenceStatus.NEEDS_ROOM)
                        .build())
                .toList();
        occurrenceRepository.saveAll(missing);

        log.info("Recálculo de ocurrencias del evento {}: +{} creadas, -{} borradas, {} asignadas conservadas",
                event.getId(), missing.size(), deletable.size(), surplus.size() - deletable.size());
    }
}
