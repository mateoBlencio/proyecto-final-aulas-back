package ar.edu.utn.frc.siga.allocation.service.impl;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    Map<OccurrenceSlotDto, Long> resolveClassroomByOccurrence(List<AllocationItem> items, LocalDate clampFrom) {
        Map<Long, List<OccurrenceSlotDto>> slotsByEvent = collectSlotsByEvent(
                items.stream().map(AllocationItem::target).toList(), clampFrom);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = new LinkedHashMap<>();
        for (AllocationItem item : items) {
            for (OccurrenceSlotDto occurrence : resolveApplicable(item.target(), clampFrom, slotsByEvent)) {
                Long previous = classroomByOccurrence.putIfAbsent(occurrence, item.classroomId());
                if (previous != null) {
                    throw new AllocationConflictException(
                            "La ocurrencia " + occurrence.occurrenceId() + " está apuntada por más de un item del lote.");
                }
            }
        }
        return classroomByOccurrence;
    }

    List<OccurrenceSlotDto> resolveAll(List<AllocationTarget> targets, LocalDate clampFrom) {
        Map<Long, List<OccurrenceSlotDto>> slotsByEvent = collectSlotsByEvent(targets, clampFrom);
        Set<OccurrenceSlotDto> occurrences = new LinkedHashSet<>();
        for (AllocationTarget target : targets) {
            occurrences.addAll(resolveApplicable(target, clampFrom, slotsByEvent));
        }
        return List.copyOf(occurrences);
    }

    private Map<Long, List<OccurrenceSlotDto>> collectSlotsByEvent(Collection<AllocationTarget> targets, LocalDate clampFrom) {
        Set<Long> eventIds = new LinkedHashSet<>();
        for (AllocationTarget target : targets) {
            if (target instanceof AllocationTarget.Event(Long eventId)) {
                eventIds.add(eventId);
            }
        }
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        List<OccurrenceSlotDto> slots = clampFrom == null
                ? occurrenceService.findSlotsByEvents(eventIds)
                : occurrenceService.findSlotsByEvents(eventIds, clampFrom);
        return slots.stream().collect(Collectors.groupingBy(OccurrenceSlotDto::eventId));
    }

    private List<OccurrenceSlotDto> resolveApplicable(AllocationTarget target, LocalDate clampFrom,
            Map<Long, List<OccurrenceSlotDto>> slotsByEvent) {
        return switch (target) {
            case AllocationTarget.Occurrences(List<Long> occurrenceIds) -> {
                List<OccurrenceSlotDto> occurrences = occurrenceService.findSlots(occurrenceIds);
                validator.validateOccurrencesExist(occurrenceIds, occurrences);
                occurrences.forEach(validator::validateNotPast);
                yield occurrences;
            }
            case AllocationTarget.Event(Long eventId) -> slotsByEvent.getOrDefault(eventId, List.of())
                    .stream()
                    .filter(o -> clampFrom == null || !o.isPast())
                    .toList();
            case AllocationTarget.EventRange(Long eventId, LocalDate from, LocalDate to) -> {
                validator.validateRange(from, to);
                // Se ignora el clamp del comando: el rango trae su propio 'desde', ya validado.
                // El 'hasta' se filtra acá (no en la base) porque es un solo evento acotado.
                List<OccurrenceSlotDto> occurrences = occurrenceService.findSlotsByEvent(eventId, from).stream()
                        .filter(o -> to == null || !o.date().isAfter(to))
                        .toList();
                occurrences.forEach(validator::validateNotPast);
                yield occurrences;
            }
        };
    }
}
