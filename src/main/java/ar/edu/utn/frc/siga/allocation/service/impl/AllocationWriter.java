package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.util.Maps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Component
@RequiredArgsConstructor
class AllocationWriter {

    private final AllocationRepository allocationRepository;

    List<Allocation> create(Map<OccurrenceSlotDto, Integer> classroomByOccurrence, String observation, AllocationSource source) {
        List<Long> occurrenceIds = classroomByOccurrence.keySet().stream().map(OccurrenceSlotDto::occurrenceId).toList();
        List<Allocation> existing = allocationRepository.findByOccurrenceIdIn(occurrenceIds);
        if (!existing.isEmpty()) {
            throw new AllocationConflictException(
                    "La(s) ocurrencia(s) " + existing.stream().map(Allocation::getOccurrenceId).toList() + " ya tiene(n) una asignación.");
        }
        return write(classroomByOccurrence, observation, source);
    }

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
                        .observation(observation)
                        .build());
            }
            saved.add(allocation);
        }
        return saved;
    }

    List<DeallocatedOccurrence> delete(List<OccurrenceSlotDto> occurrences) {
        List<Long> occurrenceIds = occurrences.stream().map(OccurrenceSlotDto::occurrenceId).toList();
        List<Allocation> existing = allocationRepository.findByOccurrenceIdIn(occurrenceIds);
        if (existing.isEmpty()) return List.of();

        allocationRepository.deleteAll(existing);
        return existing.stream().map(a -> new DeallocatedOccurrence(a.getOccurrenceId(), a.getClassroomId())).toList();
    }

    record DeallocatedOccurrence(Long occurrenceId, Integer classroomId) {}
}
