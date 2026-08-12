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

/**
 * Traduce cualquier {@link AllocationTarget} a las occurrences que describe, aplicando en
 * el mismo paso la regla que depende de CÓMO se nombró el target (no de qué source es la
 * operación):
 *
 * <ul>
 *   <li>{@code Occurrences} (explícito): pedido puntual de una persona sobre occurrences
 *   nombradas una por una → pasada es un 409, no un skip silencioso.</li>
 *   <li>{@code Event}: pedido sobre "todas las de este evento" → las pasadas (cuando
 *   corresponde clampear) se saltean por diseño, no son un fallo parcial (mismo
 *   comportamiento que el {@code AllocationWriter} de antes).</li>
 * </ul>
 *
 * El clamp de fecha para {@code Event} lo decide el caller vía {@code clampFrom}:
 * {@code null} trae todas las occurrences (import, incluye pasadas aposta);
 * {@code LocalDate.now()} acota a las futuras (manual/automático).
 */
@Component
@RequiredArgsConstructor
class AllocationTargetResolver {

    private final OccurrenceService occurrenceService;
    private final AllocationValidator validator;

    /**
     * Resuelve todos los items del lote y arma, para cada occurrence resultante, el aula
     * que le corresponde según el item del que salió. Una misma occurrence solo puede salir
     * de un item del lote (si dos items la nombran, es un lote inválido: no hay forma de
     * saber qué aula gana) — se detecta y corta antes de escribir nada.
     */
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

    /** Igual que {@link #resolveClassroomByOccurrence} pero sin aula (desasignación): unión de occurrences sin duplicados. */
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
