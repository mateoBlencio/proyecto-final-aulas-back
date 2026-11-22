package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.common.util.Plurals;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
class DeactivatedClassroomReleaser {

    private final AllocationRepository allocationRepository;
    private final OccurrenceService occurrenceService;
    private final ClassroomService classroomService;
    private final AllocationWriter writer;
    private final ClassroomAllocationLock classroomLock;

    record Plan(List<OccurrenceSlotDto> releasedPrincipals,
                List<OccurrenceSlotDto> retiredMirrors,
                Map<OccurrenceSlotDto, Long> movedPrincipals,
                Map<Long, Allocation> allocationByOccurrence,
                Map<Long, String> noteByOccurrence,
                int mergedClasses) {

        int releasedClasses() {
            return releasedPrincipals.size();
        }

        boolean isEmpty() {
            return releasedPrincipals.isEmpty() && retiredMirrors.isEmpty() && movedPrincipals.isEmpty();
        }
    }

    record DeactivationResult(int releasedClasses, int mergedClasses) {}

    private record SourceNote(AllocationSource source, String note) {}

    Plan plan(Map<Long, String> noteByClassroom) {
        Set<Long> deactivated = noteByClassroom.keySet();
        List<Allocation> inDeactivated = allocationRepository
                .findByClassroomIdInFromDate(deactivated, LocalDate.now());
        if (inDeactivated.isEmpty()) {
            return emptyPlan();
        }
        List<OccurrenceSlotDto> startingSlots = occurrenceService
                .findSlots(inDeactivated.stream().map(Allocation::getOccurrenceId).toList()).stream()
                .filter(o -> !o.isPast())
                .toList();
        if (startingSlots.isEmpty()) {
            return emptyPlan();
        }

        Set<Long> principalIds = startingSlots.stream()
                .map(o -> o.mirrorOfOccurrenceId() != null ? o.mirrorOfOccurrenceId() : o.occurrenceId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, OccurrenceSlotDto> principalById = occurrenceService.findSlots(principalIds).stream()
                .collect(Collectors.toMap(OccurrenceSlotDto::occurrenceId, Function.identity()));
        Map<Long, List<OccurrenceSlotDto>> mirrorsByPrincipal = occurrenceService.findMirrors(principalIds).stream()
                .filter(o -> o.status() != OccurrenceStatus.ROOM_RELEASED)
                .collect(Collectors.groupingBy(OccurrenceSlotDto::mirrorOfOccurrenceId));

        List<Long> involvedIds = new ArrayList<>(principalIds);
        mirrorsByPrincipal.values().forEach(mirrors -> mirrors.forEach(m -> involvedIds.add(m.occurrenceId())));
        Map<Long, Allocation> allocationByOccurrence = allocationRepository.findByOccurrenceIdIn(involvedIds).stream()
                .collect(Collectors.toMap(Allocation::getOccurrenceId, Function.identity()));
        Map<Long, Integer> capacityByClassroom = capacities(allocationByOccurrence.values());

        List<OccurrenceSlotDto> released = new ArrayList<>();
        List<OccurrenceSlotDto> retired = new ArrayList<>();
        Map<OccurrenceSlotDto, Long> moved = new LinkedHashMap<>();
        Map<Long, String> noteByOccurrence = new LinkedHashMap<>();
        int merged = 0;

        for (Long principalId : principalIds) {
            OccurrenceSlotDto principal = principalById.get(principalId);
            Allocation principalAllocation = allocationByOccurrence.get(principalId);
            List<OccurrenceSlotDto> mirrors = mirrorsByPrincipal.getOrDefault(principalId, List.of());

            List<OccurrenceSlotDto> mirrorsInDeactivated = mirrors.stream()
                    .filter(m -> isIn(allocationByOccurrence.get(m.occurrenceId()), deactivated))
                    .toList();
            List<OccurrenceSlotDto> remainingMirrors = mirrors.stream()
                    .filter(m -> allocationByOccurrence.get(m.occurrenceId()) != null)
                    .filter(m -> !isIn(allocationByOccurrence.get(m.occurrenceId()), deactivated))
                    .toList();

            mirrorsInDeactivated.forEach(m -> {
                retired.add(m);
                noteByOccurrence.put(m.occurrenceId(), noteOf(noteByClassroom, allocationByOccurrence, m));
            });

            boolean principalInDeactivated = isIn(principalAllocation, deactivated);
            if (principalInDeactivated) {
                String principalNote = noteOf(noteByClassroom, allocationByOccurrence, principal);
                noteByOccurrence.put(principalId, principalNote);
                if (remainingMirrors.isEmpty()) {
                    released.add(principal);
                } else {
                    OccurrenceSlotDto absorbing = remainingMirrors.stream()
                            .min(Comparator
                                    .comparing((OccurrenceSlotDto m) -> -capacityByClassroom.getOrDefault(
                                            allocationByOccurrence.get(m.occurrenceId()).getClassroomId(), 0))
                                    .thenComparing(OccurrenceSlotDto::roomSlot))
                            .orElseThrow();
                    moved.put(principal, allocationByOccurrence.get(absorbing.occurrenceId()).getClassroomId());
                    retired.add(absorbing);
                    noteByOccurrence.put(absorbing.occurrenceId(), principalNote);
                    merged++;
                }
            } else if (!mirrorsInDeactivated.isEmpty()
                    && (principalAllocation != null || !remainingMirrors.isEmpty())) {
                // Fusionada solo si a la clase le queda un aula; si no, la principal ya estaba sin aula.
                merged++;
            }
        }
        return new Plan(released, retired, moved, allocationByOccurrence, noteByOccurrence, merged);
    }

    @AuditOperation(value = "Liberación y fusión de clases por baja de aula", subject = "Allocation")
    public DeactivationResult apply(Map<Long, String> noteByClassroom) {
        Plan plan = plan(noteByClassroom);
        if (plan.isEmpty()) {
            return new DeactivationResult(0, 0);
        }
        AuditOperations.describe("Liberación y fusión de clases por baja de aula ("
                + Plurals.count(plan.releasedClasses(), "liberada", "liberadas") + ", "
                + Plurals.count(plan.mergedClasses(), "fusionada", "fusionadas") + ")");

        classroomLock.lock(plan.movedPrincipals().values());

        List<OccurrenceSlotDto> toDelete = new ArrayList<>(plan.retiredMirrors());
        toDelete.addAll(plan.releasedPrincipals());
        groupByNote(toDelete, plan).forEach((note, occurrences) -> writer.delete(occurrences, note));

        Map<SourceNote, Map<OccurrenceSlotDto, Long>> movedBySourceNote = new LinkedHashMap<>();
        plan.movedPrincipals().forEach((principal, classroomId) -> movedBySourceNote
                .computeIfAbsent(new SourceNote(plan.allocationByOccurrence().get(principal.occurrenceId()).getSource(),
                        plan.noteByOccurrence().get(principal.occurrenceId())), k -> new LinkedHashMap<>())
                .put(principal, classroomId));
        movedBySourceNote.forEach((key, classroomByOccurrence) ->
                writer.restore(classroomByOccurrence, key.note(), key.source()));

        release(plan.retiredMirrors(), ReleaseReason.MIRROR_RETIRED, plan);
        release(plan.releasedPrincipals(), ReleaseReason.CLASSROOM_DEACTIVATED, plan);
        log.info("Baja de aula: clasesLiberadas={}, clasesFusionadas={}", plan.releasedClasses(), plan.mergedClasses());
        return new DeactivationResult(plan.releasedClasses(), plan.mergedClasses());
    }

    private void release(List<OccurrenceSlotDto> occurrences, ReleaseReason reason, Plan plan) {
        groupByNote(occurrences, plan).forEach((note, group) -> occurrenceService.release(
                group.stream()
                        .map(o -> new OccurrenceRelease(o.occurrenceId(),
                                plan.allocationByOccurrence().get(o.occurrenceId()).getClassroomId()))
                        .toList(),
                reason, note));
    }

    private static Map<String, List<OccurrenceSlotDto>> groupByNote(List<OccurrenceSlotDto> occurrences, Plan plan) {
        return occurrences.stream().collect(Collectors.groupingBy(
                o -> plan.noteByOccurrence().get(o.occurrenceId()), LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, Integer> capacities(Collection<Allocation> allocations) {
        Set<Long> classroomIds = allocations.stream().map(Allocation::getClassroomId).collect(Collectors.toSet());
        return classroomService.findByIdsIncludingDeactivated(classroomIds).stream()
                .collect(Collectors.toMap(ClassroomResponseDto::id, ClassroomResponseDto::capacity));
    }

    private static boolean isIn(Allocation allocation, Set<Long> classroomIds) {
        return allocation != null && classroomIds.contains(allocation.getClassroomId());
    }

    private static String noteOf(Map<Long, String> noteByClassroom, Map<Long, Allocation> allocationByOccurrence,
            OccurrenceSlotDto occurrence) {
        return noteByClassroom.get(allocationByOccurrence.get(occurrence.occurrenceId()).getClassroomId());
    }

    private static Plan emptyPlan() {
        return new Plan(List.of(), List.of(), Map.of(), Map.of(), Map.of(), 0);
    }
}
