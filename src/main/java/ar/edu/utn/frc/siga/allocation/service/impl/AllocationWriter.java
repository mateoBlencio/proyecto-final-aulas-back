package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Único punto de escritura de asignaciones: upsert por ocurrencia + pase a ASSIGNED.
 * Toda fuente (manual, importada, automática) pasa por acá; el intent method que llama
 * decide validaciones previas y estampa su source.
 */
@Component
@RequiredArgsConstructor
class AllocationWriter {

    private final AllocationRepository allocationRepository;
    private final AllocationValidator validator;
    private final OccurrenceService occurrenceService;

    /**
     * Crea o actualiza la asignación de cada ocurrencia de la lista al aula indicada,
     * y la pasa a ASSIGNED. Las no-asignables (CANCELLED/SUSPENDED, o pasadas cuando
     * {@code skipPast}) se saltean por diseño, no son un fallo parcial.
     * Corre siempre dentro de la transacción del caller: la allocation existente llega
     * managed, así que las actualizaciones se persisten por dirty checking sin necesidad
     * de {@code save()} explícito; solo las allocations nuevas lo requieren.
     */
    List<Allocation> apply(List<OccurrenceSlotDto> occurrences, Integer classroomId, String observation,
                            AllocationSource source, boolean skipPast) {
        return apply(occurrences, o -> classroomId, observation, source, skipPast);
    }

    /**
     * Igual que {@link #apply(List, Integer, String, AllocationSource, boolean)} pero con aula
     * resuelta por ocurrencia: permite aplicar occurrences de varios eventos (aula distinta por
     * evento) en una sola pasada, con una única query de asignaciones existentes en vez de una
     * por evento (evita N+1 cuando el caller agrupa por evento, p. ej. confirm de auto-preview).
     */
    List<Allocation> apply(List<OccurrenceSlotDto> occurrences, Function<OccurrenceSlotDto, Integer> classroomIdResolver,
                            String observation, AllocationSource source, boolean skipPast) {
        Map<Long, Allocation> existingByOccurrence = allocationRepository
                .findByOccurrenceIdIn(occurrences.stream().map(OccurrenceSlotDto::occurrenceId).toList())
                .stream().collect(Collectors.toMap(Allocation::getOccurrenceId, a -> a));

        List<Allocation> saved = new ArrayList<>();
        List<Long> assignedOccurrenceIds = new ArrayList<>();
        for (OccurrenceSlotDto occurrence : occurrences) {
            if (skipPast && occurrence.isPast()) continue;
            if (!validator.isAssignable(occurrence)) continue;

            Integer classroomId = classroomIdResolver.apply(occurrence);
            Allocation existing = existingByOccurrence.get(occurrence.occurrenceId());
            Allocation allocation;
            if (existing != null) {
                existing.setClassroomId(classroomId);
                existing.setSource(source);
                existing.setObservation(observation);
                allocation = existing;
            } else {
                allocation = allocationRepository.save(Allocation.builder()
                        .occurrenceId(occurrence.occurrenceId())
                        .classroomId(classroomId)
                        .source(source)
                        .createdAt(LocalDateTime.now())
                        .observation(observation)
                        .build());
            }
            saved.add(allocation);
            assignedOccurrenceIds.add(occurrence.occurrenceId());
        }
        if (!assignedOccurrenceIds.isEmpty()) {
            occurrenceService.markAssigned(assignedOccurrenceIds);
        }
        return saved;
    }
}
