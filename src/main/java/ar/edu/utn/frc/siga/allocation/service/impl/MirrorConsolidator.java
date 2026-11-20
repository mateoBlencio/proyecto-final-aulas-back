package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Saca el aula de una principal pasándola al aula de uno de sus espejos. La principal conserva su identidad
 * y cambia de aula; el espejo elegido queda liberado como {@code MIRROR_RETIRED}.
 */
@Component
@RequiredArgsConstructor
class MirrorConsolidator {

    static final int NOTE_MAX_LENGTH = 255;

    private final OccurrenceService occurrenceService;
    private final AllocationRepository allocationRepository;
    private final ClassroomService classroomService;
    private final AllocationWriter writer;
    private final ClassroomAllocationLock classroomLock;

    record Consolidation(OccurrenceSlotDto principal, OccurrenceSlotDto mirror, Long fromClassroomId,
                         Long toClassroomId) {}

    /**
     * {@code withoutMirror}: principales sin ningún espejo asignado que pueda quedar vivo.
     * {@code blocked}: tienen espejos asignados pero ninguno con aula disponible.
     */
    record Plan(List<Consolidation> consolidations, List<OccurrenceSlotDto> withoutMirror,
                List<OccurrenceSlotDto> blocked) {

        List<OccurrenceSlotDto> withoutAlternative() {
            List<OccurrenceSlotDto> all = new ArrayList<>(withoutMirror);
            all.addAll(blocked);
            return all;
        }
    }

    /**
     * Sin efectos. Los espejos candidatos son los futuros, no liberados, con asignación y con aula
     * disponible, fuera de {@code excludedMirrorIds}. Gana el de aula más grande y, a igual capacidad,
     * el de menor {@code roomSlot}, el mismo criterio de la baja de aula.
     */
    Plan plan(Collection<OccurrenceSlotDto> principals, Set<Long> excludedMirrorIds) {
        if (principals.isEmpty()) {
            return new Plan(List.of(), List.of(), List.of());
        }
        List<Long> principalIds = principals.stream().map(OccurrenceSlotDto::occurrenceId).toList();
        Map<Long, List<OccurrenceSlotDto>> mirrorsByPrincipal = occurrenceService.findMirrors(principalIds).stream()
                .filter(m -> !m.isPast())
                .filter(m -> m.status() != OccurrenceStatus.ROOM_RELEASED)
                .filter(m -> !excludedMirrorIds.contains(m.occurrenceId()))
                .collect(Collectors.groupingBy(OccurrenceSlotDto::mirrorOfOccurrenceId));

        List<Long> involvedIds = new ArrayList<>(principalIds);
        mirrorsByPrincipal.values().forEach(mirrors -> mirrors.forEach(m -> involvedIds.add(m.occurrenceId())));
        Map<Long, Long> classroomByOccurrence = allocationRepository.findByOccurrenceIdIn(involvedIds).stream()
                .collect(Collectors.toMap(Allocation::getOccurrenceId, Allocation::getClassroomId));
        Set<Long> mirrorClassroomIds = mirrorsByPrincipal.values().stream()
                .flatMap(List::stream)
                .map(m -> classroomByOccurrence.get(m.occurrenceId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Integer> capacityByAvailableClassroom = classroomService.findByIds(mirrorClassroomIds).stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, ClassroomResponseDto::capacity));

        List<Consolidation> consolidations = new ArrayList<>();
        List<OccurrenceSlotDto> withoutMirror = new ArrayList<>();
        List<OccurrenceSlotDto> blocked = new ArrayList<>();
        for (OccurrenceSlotDto principal : principals) {
            List<OccurrenceSlotDto> assigned = mirrorsByPrincipal.getOrDefault(principal.occurrenceId(), List.of())
                    .stream()
                    .filter(m -> classroomByOccurrence.containsKey(m.occurrenceId()))
                    .toList();
            if (assigned.isEmpty()) {
                withoutMirror.add(principal);
                continue;
            }
            Optional<OccurrenceSlotDto> target = assigned.stream()
                    .filter(m -> capacityByAvailableClassroom.containsKey(classroomByOccurrence.get(m.occurrenceId())))
                    .min(Comparator
                            .comparing((OccurrenceSlotDto m) -> -capacityByAvailableClassroom
                                    .get(classroomByOccurrence.get(m.occurrenceId())))
                            .thenComparing(OccurrenceSlotDto::roomSlot));
            if (target.isPresent()) {
                OccurrenceSlotDto mirror = target.get();
                consolidations.add(new Consolidation(principal, mirror,
                        classroomByOccurrence.get(principal.occurrenceId()),
                        classroomByOccurrence.get(mirror.occurrenceId())));
            } else {
                blocked.add(principal);
            }
        }
        return new Plan(consolidations, withoutMirror, blocked);
    }

    void apply(List<Consolidation> consolidations, String note) {
        if (consolidations.isEmpty()) {
            return;
        }
        Set<Long> classroomIds = new HashSet<>();
        consolidations.forEach(c -> {
            classroomIds.add(c.toClassroomId());
            if (c.fromClassroomId() != null) {
                classroomIds.add(c.fromClassroomId());
            }
        });
        classroomLock.lock(classroomIds);

        consolidations.forEach(c -> writer.consolidate(c.principal(), c.mirror(), c.toClassroomId(), note));
        occurrenceService.release(
                consolidations.stream()
                        .map(c -> new OccurrenceRelease(c.mirror().occurrenceId(), c.toClassroomId()))
                        .toList(),
                ReleaseReason.MIRROR_RETIRED, note);
    }

    static String truncate(String note) {
        return note.length() <= NOTE_MAX_LENGTH ? note : note.substring(0, NOTE_MAX_LENGTH);
    }
}
