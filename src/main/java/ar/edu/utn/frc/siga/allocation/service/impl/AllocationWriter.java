package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Único punto de escritura de asignaciones: upsert por ocurrencia + pase a ASSIGNED.
 * Toda fuente (manual, importada, automática) pasa por acá; el intent method que llama
 * decide validaciones previas y estampa su source. Gancho a futuro: cuando exista
 * notificación al docente, este será el único lugar que publique el evento de dominio
 * (AllocationChangedEvent).
 */
@Component
@RequiredArgsConstructor
class AllocationWriter {

    private final AllocationRepository allocationRepository;
    private final AllocationValidator validator;

    /**
     * Crea o actualiza la asignación de cada ocurrencia de la lista al aula indicada,
     * y la pasa a ASSIGNED. Las no-asignables (CANCELLED/SUSPENDED, o pasadas cuando
     * {@code skipPast}) se saltean por diseño, no son un fallo parcial.
     * Corre siempre dentro de la transacción del caller: occurrence y allocation existente
     * llegan managed, así que las actualizaciones se persisten por dirty checking sin
     * necesidad de {@code save()} explícito; solo las allocations nuevas lo requieren.
     */
    List<Allocation> apply(List<Occurrence> occurrences, Integer classroomId, String observation,
                            AllocationSource source, boolean skipPast) {
        Map<Long, Allocation> existingByOccurrence = allocationRepository
                .findByOccurrence_IdIn(occurrences.stream().map(Occurrence::getId).toList())
                .stream().collect(Collectors.toMap(a -> a.getOccurrence().getId(), a -> a));

        List<Allocation> saved = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            if (skipPast && occurrence.isPast()) continue;
            if (!validator.isAssignable(occurrence)) continue;

            Allocation existing = existingByOccurrence.get(occurrence.getId());
            Allocation allocation;
            if (existing != null) {
                existing.setClassroomId(classroomId);
                existing.setSource(source);
                existing.setObservation(observation);
                allocation = existing;
            } else {
                allocation = allocationRepository.save(Allocation.builder()
                        .occurrence(occurrence)
                        .classroomId(classroomId)
                        .source(source)
                        .createdAt(LocalDateTime.now())
                        .observation(observation)
                        .build());
            }
            saved.add(allocation);

            occurrence.setStatus(OccurrenceStatus.ASSIGNED);
        }
        return saved;
    }
}
