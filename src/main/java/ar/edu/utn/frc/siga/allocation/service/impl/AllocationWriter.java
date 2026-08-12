package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.util.Maps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Único punto de escritura de asignaciones: por cada (occurrence, aula) ya resuelto por
 * {@link AllocationTargetResolver}, crea o actualiza la fila. Toda fuente (manual,
 * importada, automática) pasa por acá; el verbo que llama ({@code allocate} estricto vs
 * {@code reallocate} upsert) decide qué hacer si ya existía. No toca {@code Occurrence}:
 * "tiene aula" es {@code existe fila acá}, no un estado que sincronizar (ver
 * {@code OccurrenceStatus}). Corre siempre dentro de la transacción del caller: la
 * allocation existente llega managed, así que las actualizaciones se persisten por dirty
 * checking sin {@code save()} explícito; solo las allocations nuevas lo requieren.
 */
@Component
@RequiredArgsConstructor
class AllocationWriter {

    private final AllocationRepository allocationRepository;

    /**
     * Crea la asignación de cada occurrence de {@code classroomByOccurrence}. Corta con 409
     * si CUALQUIERA ya tiene asignación — nada se escribe (se chequea el lote completo antes
     * de la primera escritura).
     */
    List<Allocation> create(Map<OccurrenceSlotDto, Integer> classroomByOccurrence, String observation, AllocationSource source) {
        List<Long> occurrenceIds = classroomByOccurrence.keySet().stream().map(OccurrenceSlotDto::occurrenceId).toList();
        List<Allocation> existing = allocationRepository.findByOccurrenceIdIn(occurrenceIds);
        if (!existing.isEmpty()) {
            throw new AllocationConflictException(
                    "La(s) ocurrencia(s) " + existing.stream().map(Allocation::getOccurrenceId).toList() + " ya tiene(n) una asignación.");
        }
        return write(classroomByOccurrence, observation, source);
    }

    /** Crea o actualiza (upsert) la asignación de cada occurrence de {@code classroomByOccurrence}. */
    List<Allocation> upsert(Map<OccurrenceSlotDto, Integer> classroomByOccurrence, String observation, AllocationSource source) {
        return write(classroomByOccurrence, observation, source);
    }

    private List<Allocation> write(Map<OccurrenceSlotDto, Integer> classroomByOccurrence, String observation, AllocationSource source) {
        if (classroomByOccurrence.isEmpty()) return List.of();

        List<Long> occurrenceIds = classroomByOccurrence.keySet().stream().map(OccurrenceSlotDto::occurrenceId).toList();
        Map<Long, Allocation> existingByOccurrence = Maps.byId(
                allocationRepository.findByOccurrenceIdIn(occurrenceIds), Allocation::getOccurrenceId);

        List<Allocation> saved = new ArrayList<>();
        for (Entry<OccurrenceSlotDto, Integer> entry : classroomByOccurrence.entrySet()) {
            OccurrenceSlotDto occurrence = entry.getKey();
            Integer classroomId = entry.getValue();
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
        }
        return saved;
    }

    /** Borra la asignación de cada occurrence indicada, si existe. Occurrences sin asignación se ignoran. */
    List<DeallocatedOccurrence> delete(List<OccurrenceSlotDto> occurrences) {
        List<Long> occurrenceIds = occurrences.stream().map(OccurrenceSlotDto::occurrenceId).toList();
        List<Allocation> existing = allocationRepository.findByOccurrenceIdIn(occurrenceIds);
        if (existing.isEmpty()) return List.of();

        allocationRepository.deleteAll(existing);
        return existing.stream().map(a -> new DeallocatedOccurrence(a.getOccurrenceId(), a.getClassroomId())).toList();
    }

    /** Occurrence liberada y el aula que tenía hasta ahora. */
    record DeallocatedOccurrence(Long occurrenceId, Integer classroomId) {}
}
