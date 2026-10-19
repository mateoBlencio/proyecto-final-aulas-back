package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.util.Maps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
class AllocationWriter {

    private static final String SYSACAD_CREATED_OBSERVATION = "Recuperado de SysAcad";
    private static final String SYSACAD_UPDATED_OBSERVATION = "Actualizado por sync de SysAcad";

    private final AllocationRepository allocationRepository;
    private final ClassroomMovementPublisher movementPublisher;

    List<Allocation> create(Map<OccurrenceSlotDto, Long> classroomByOccurrence, String observation, AllocationSource source) {
        List<Long> occurrenceIds = classroomByOccurrence.keySet().stream().map(OccurrenceSlotDto::occurrenceId).toList();
        List<Allocation> existing = allocationRepository.findByOccurrenceIdIn(occurrenceIds);
        if (!existing.isEmpty()) {
            throw new AllocationConflictException(
                    "La(s) ocurrencia(s) " + existing.stream().map(Allocation::getOccurrenceId).toList() + " ya tiene(n) una asignación.");
        }
        return write(classroomByOccurrence, observation, source);
    }

    List<Allocation> upsert(Map<OccurrenceSlotDto, Long> classroomByOccurrence, String observation, AllocationSource source) {
        return write(classroomByOccurrence, observation, source);
    }

    private List<Allocation> write(Map<OccurrenceSlotDto, Long> classroomByOccurrence, String observation, AllocationSource source) {
        if (classroomByOccurrence.isEmpty()) return List.of();

        List<Long> occurrenceIds = classroomByOccurrence.keySet().stream().map(OccurrenceSlotDto::occurrenceId).toList();
        Map<Long, Allocation> existingByOccurrence = Maps.byId(
                allocationRepository.findByOccurrenceIdIn(occurrenceIds), Allocation::getOccurrenceId);

        List<Allocation> saved = new ArrayList<>();
        List<Change> changes = new ArrayList<>();
        for (Entry<OccurrenceSlotDto, Long> entry : classroomByOccurrence.entrySet()) {
            OccurrenceSlotDto occurrence = entry.getKey();
            Long classroomId = entry.getValue();
            Allocation existing = existingByOccurrence.get(occurrence.occurrenceId());
            Long fromClassroomId = existing == null ? null : existing.getClassroomId();
            if (!Objects.equals(fromClassroomId, classroomId)) {
                changes.add(new Change(occurrence.occurrenceId(), occurrence.eventId(), occurrence.date(),
                        occurrence.startTime(), occurrence.endTime(), fromClassroomId, classroomId));
            }
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
        movementPublisher.publish(source, observation, changes);
        return saved;
    }

    /**
     * Escritura del sync ASIGNACIONES de SysAcad: a diferencia de {@link #create}/{@link #upsert}, no
     * es "todo o nada" por lote — la política de precedencia se decide ocurrencia por ocurrencia (ver
     * .claude/docs/plan-sync-eventos-sysacad.md §4): sin asignación -> crea (source=SYSACAD); con
     * asignación propia (source=SYSACAD) -> actualiza el aula solo si cambió; con asignación de otro origen (humano o
     * import de Excel) -> no se toca, WARN. Devuelve la cantidad de ocurrencias creadas+actualizadas.
     */
    int syncFromSysacad(Map<OccurrenceSlotDto, Long> classroomByOccurrence) {
        if (classroomByOccurrence.isEmpty()) return 0;

        List<Long> occurrenceIds = classroomByOccurrence.keySet().stream().map(OccurrenceSlotDto::occurrenceId).toList();
        Map<Long, Allocation> existingByOccurrence = Maps.byId(
                allocationRepository.findByOccurrenceIdIn(occurrenceIds), Allocation::getOccurrenceId);

        int affected = 0;
        for (Entry<OccurrenceSlotDto, Long> entry : classroomByOccurrence.entrySet()) {
            OccurrenceSlotDto occurrence = entry.getKey();
            Long classroomId = entry.getValue();
            Allocation existing = existingByOccurrence.get(occurrence.occurrenceId());

            if (existing == null) {
                allocationRepository.save(Allocation.builder()
                        .occurrenceId(occurrence.occurrenceId())
                        .classroomId(classroomId)
                        .source(AllocationSource.SYSACAD)
                        .observation(SYSACAD_CREATED_OBSERVATION)
                        .build());
                affected++;
            } else if (existing.getSource() == AllocationSource.SYSACAD) {
                if (classroomId.equals(existing.getClassroomId())) {
                    continue;
                }
                existing.setClassroomId(classroomId);
                existing.setObservation(SYSACAD_UPDATED_OBSERVATION);
                affected++;
            } else {
                log.warn("Ocurrencia {} ya tiene una asignación de origen {}; el sync de SysAcad no la pisa",
                        occurrence.occurrenceId(), existing.getSource());
            }
        }
        return affected;
    }

    List<DeallocatedOccurrence> delete(List<OccurrenceSlotDto> occurrences, String observation) {
        List<Long> occurrenceIds = occurrences.stream().map(OccurrenceSlotDto::occurrenceId).toList();
        List<Allocation> existing = allocationRepository.findByOccurrenceIdIn(occurrenceIds);
        if (existing.isEmpty()) return List.of();

        allocationRepository.deleteAll(existing);
        Map<Long, OccurrenceSlotDto> slotById = Maps.byId(occurrences, OccurrenceSlotDto::occurrenceId, (a, b) -> a);
        List<Change> changes = existing.stream().map(a -> {
            OccurrenceSlotDto slot = slotById.get(a.getOccurrenceId());
            return new Change(a.getOccurrenceId(), slot.eventId(), slot.date(), slot.startTime(), slot.endTime(),
                    a.getClassroomId(), null);
        }).toList();
        movementPublisher.publish(AllocationSource.MANUAL, observation, changes);
        return existing.stream().map(a -> new DeallocatedOccurrence(a.getOccurrenceId(), a.getClassroomId())).toList();
    }

    record DeallocatedOccurrence(Long occurrenceId, Long classroomId) {}
}
