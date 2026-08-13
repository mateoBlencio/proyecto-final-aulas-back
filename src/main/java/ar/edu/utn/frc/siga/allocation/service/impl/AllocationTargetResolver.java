package ar.edu.utn.frc.siga.allocation.service.impl;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class AllocationTargetResolver {

    private final OccurrenceService occurrenceService;
    private final AllocationValidator validator;

    Map<OccurrenceSlotDto, Integer> resolveClassroomByOccurrence(List<AllocationItem> items, LocalDate clampFrom) {
        Map<OccurrenceSlotDto, Integer> classroomByOccurrence = new LinkedHashMap<>();
        for (AllocationItem item : items) {
            for (OccurrenceSlotDto occurrence : resolveApplicable(item.target(), clampFrom)) {
                Integer previous = classroomByOccurrence.putIfAbsent(occurrence, item.classroomId());
                if (previous != null) {
                    throw new AllocationConflictException(
                            "La ocurrencia " + occurrence.occurrenceId() + " está apuntada por más de un item del lote.");
                }
            }
        }
        return classroomByOccurrence;
    }

    List<OccurrenceSlotDto> resolveAll(List<AllocationTarget> targets, LocalDate clampFrom) {
        Set<OccurrenceSlotDto> occurrences = new LinkedHashSet<>();
        for (AllocationTarget target : targets) {
            occurrences.addAll(resolveApplicable(target, clampFrom));
        }
        return List.copyOf(occurrences);
    }

    private List<OccurrenceSlotDto> resolveApplicable(AllocationTarget target, LocalDate clampFrom) {
        return switch (target) {
            case AllocationTarget.Occurrences(List<Long> occurrenceIds) -> {
                List<OccurrenceSlotDto> occurrences = occurrenceService.findSlots(occurrenceIds);
                occurrences.forEach(validator::validateNotPast);
                yield occurrences;
            }
            case AllocationTarget.Event(Long eventId) -> occurrenceService.findSlotsByEvent(eventId, clampFrom)
                    .stream()
                    .filter(o -> clampFrom == null || !o.isPast())
                    .toList();
        };
    }
}
