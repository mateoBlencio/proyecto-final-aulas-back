package ar.edu.utn.frc.siga.allocation.service.impl;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class AllocationTargetResolver {

    private final OccurrenceService occurrenceService;
    private final AllocationValidator validator;

    record ResolvedAssignments(Map<OccurrenceSlotDto, Long> classroomByOccurrence,
                               List<OccurrenceSlotDto> skippedReleased,
                               List<OccurrenceSlotDto> reactivated) {}

    // Una ocurrencia liberada es una decisión humana: ningún camino que escribe asignaciones la pisa
    // (decisiones D3 y D9), sea cual sea la forma del target. La liberada por baja de aula
    // (CLASSROOM_DEACTIVATED) se reasigna directo solo cuando el llamador lo pide.
    // Los targets Event y EventRange resuelven solo la ocurrencia principal: las aulas adicionales
    // (espejos) conservan la suya.
    ResolvedAssignments resolveClassroomByOccurrence(List<AllocationItem> items, LocalDate clampFrom,
            boolean reassignDeactivationReleases) {
        Map<Long, List<OccurrenceSlotDto>> slotsByEvent = collectSlotsByEvent(
                items.stream().map(AllocationItem::target).toList(), clampFrom);
        Map<OccurrenceSlotDto, Long> classroomByOccurrence = new LinkedHashMap<>();
        Set<OccurrenceSlotDto> skippedReleased = new LinkedHashSet<>();
        Set<OccurrenceSlotDto> reactivated = new LinkedHashSet<>();
        for (AllocationItem item : items) {
            for (OccurrenceSlotDto occurrence : resolveApplicable(item.target(), clampFrom, slotsByEvent, true)) {
                if (occurrence.status() == OccurrenceStatus.ROOM_RELEASED) {
                    if (!reassignDeactivationReleases || occurrence.blocksAllocation()) {
                        skippedReleased.add(occurrence);
                        continue;
                    }
                    reactivated.add(occurrence);
                }
                Long previous = classroomByOccurrence.putIfAbsent(occurrence, item.classroomId());
                if (previous != null && !previous.equals(item.classroomId())) {
                    throw new AllocationConflictException(
                            "La ocurrencia " + occurrence.occurrenceId() + " está apuntada por más de un item del lote.");
                }
            }
        }
        return new ResolvedAssignments(classroomByOccurrence, List.copyOf(skippedReleased),
                List.copyOf(reactivated));
    }

    List<OccurrenceSlotDto> resolveAll(List<AllocationTarget> targets, LocalDate clampFrom) {
        Map<Long, List<OccurrenceSlotDto>> slotsByEvent = collectSlotsByEvent(targets, clampFrom);
        Set<OccurrenceSlotDto> occurrences = new LinkedHashSet<>();
        for (AllocationTarget target : targets) {
            occurrences.addAll(resolveApplicable(target, clampFrom, slotsByEvent, false));
        }
        return List.copyOf(occurrences);
    }

    private Map<Long, List<OccurrenceSlotDto>> collectSlotsByEvent(Collection<AllocationTarget> targets, LocalDate clampFrom) {
        Set<Long> eventIds = new LinkedHashSet<>();
        for (AllocationTarget target : targets) {
            if (target instanceof AllocationTarget.Event(Long eventId, Integer roomSlot)) {
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
            Map<Long, List<OccurrenceSlotDto>> slotsByEvent, boolean principalsOnly) {
        return switch (target) {
            case AllocationTarget.Occurrences(List<Long> occurrenceIds) -> {
                List<OccurrenceSlotDto> occurrences = occurrenceService.findSlots(occurrenceIds);
                validator.validateOccurrencesExist(occurrenceIds, occurrences);
                occurrences.forEach(validator::validateNotPast);
                yield occurrences;
            }
            case AllocationTarget.Event(Long eventId, Integer roomSlot) -> slotsByEvent.getOrDefault(eventId, List.of())
                    .stream()
                    .filter(o -> !principalsOnly || o.mirrorOfOccurrenceId() == null)
                    .filter(o -> roomSlot == null || Objects.equals(o.roomSlot(), roomSlot))
                    .filter(o -> clampFrom == null || !o.isPast())
                    .toList();
            case AllocationTarget.EventRange(Long eventId, LocalDate from, LocalDate to, Integer roomSlot) -> {
                validator.validateRange(from, to);
                // Se ignora el clamp del comando: el rango trae su propio 'desde'.
                // Un 'desde' anterior a hoy se recorta a hoy sin error: el pasado no se toca.
                // El 'hasta' se filtra acá (no en la base) porque es un solo evento acotado.
                // Un cambio "desde hoy" omite la clase de hoy si ya empezó, igual que el target Event.
                LocalDate effectiveFrom = from.isBefore(LocalDate.now()) ? LocalDate.now() : from;
                yield occurrenceService.findSlotsByEvent(eventId, effectiveFrom).stream()
                        .filter(o -> !principalsOnly || o.mirrorOfOccurrenceId() == null)
                        .filter(o -> roomSlot == null || Objects.equals(o.roomSlot(), roomSlot))
                        .filter(o -> to == null || !o.date().isAfter(to))
                        .filter(o -> !o.isPast())
                        .toList();
            }
        };
    }
}
