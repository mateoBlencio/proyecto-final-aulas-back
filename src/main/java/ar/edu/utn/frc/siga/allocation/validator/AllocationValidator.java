package ar.edu.utn.frc.siga.allocation.validator;

import ar.edu.utn.frc.siga.allocation.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto.ConflictOrigin;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.TimeRanges;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Centraliza todas las reglas de negocio de asignación de aulas, compartidas entre flujo manual y automático. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllocationValidator {

    private final ClassroomService classroomService;
    private final AllocationRepository allocationRepository;
    private final OccurrenceService occurrenceService;

    /** Ocurrencia a (re)asignar y el aula destino que se le quiere dar. */
    public record AllocationCandidate(OccurrenceSlotDto occurrence, Integer classroomId) {
    }

    /** Franja ocupada, sea de BD o de un snapshot automático. */
    public record OccupiedSlot(Integer classroomId, LocalDate date, LocalTime startTime, LocalTime endTime,
                                Long eventId, Long allocationId) {

        public static OccupiedSlot from(Allocation a, OccurrenceSlotDto occurrence) {
            return new OccupiedSlot(a.getClassroomId(), occurrence.date(),
                    occurrence.startTime(), occurrence.endTime(), occurrence.eventId(), a.getId());
        }
    }

    /**
     * Propuesta ya resuelta del preview (evento → aula candidata en ciertas fechas/franja),
     * desacoplada de {@link PreviewAllocationDto} para que el núcleo de comparación no dependa
     * del request HTTP de validate-move (lo reusa también {@code unresolvedConflicts}, que no
     * tiene un {@code ValidateMoveRequestDto} a mano).
     */
    public record ResolvedProposal(Long eventId, Integer classroomId, List<LocalDate> dates,
                                    LocalTime startTime, LocalTime endTime) {
    }

    // ---------- solapamiento ----------

    /**
     * Verifica contra el estado firme de BD que ninguno de los candidatos solape con
     * asignaciones ASSIGNED existentes ni entre sí. Filtra las ocurrencias ya pasadas,
     * acota la consulta al rango de fechas del lote y excluye las propias ocurrencias de
     * los candidatos (sus asignaciones actuales se están reemplazando/moviendo). Si algo
     * choca, corta con 409; nada se escribe.
     */
    public void validateNoOverlap(List<AllocationCandidate> candidates) {
        List<AllocationCandidate> future = candidates.stream().filter(c -> !c.occurrence().isPast()).toList();
        if (future.isEmpty()) return;

        LocalDate min = future.stream().map(c -> c.occurrence().date()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = future.stream().map(c -> c.occurrence().date()).max(Comparator.naturalOrder()).orElseThrow();
        Set<Long> ownOccurrenceIds = future.stream().map(c -> c.occurrence().occurrenceId()).collect(Collectors.toSet());

        Map<Long, OccurrenceSlotDto> slotByOccurrenceId = Maps.byId(
                occurrenceService.findSlotsByStatusBetween(OccurrenceStatus.ASSIGNED, min, max),
                OccurrenceSlotDto::occurrenceId);

        List<OccupiedSlot> occupancy = allocationRepository.findByOccurrenceIdIn(slotByOccurrenceId.keySet())
                .stream()
                .filter(a -> !ownOccurrenceIds.contains(a.getOccurrenceId()))
                .map(a -> OccupiedSlot.from(a, slotByOccurrenceId.get(a.getOccurrenceId())))
                .toList();

        validateNoOverlap(future, occupancy);
    }

    /**
     * Combina {@link #validateClassroomsAvailable} (deriva el set de aulas de los candidates)
     * y {@link #validateNoOverlap(List)}: par repetido en cada intent method del flujo manual.
     */
    public void validateBatch(List<AllocationCandidate> candidates) {
        validateClassroomsAvailable(candidates.stream().map(AllocationCandidate::classroomId).collect(Collectors.toSet()));
        validateNoOverlap(candidates);
    }

    /**
     * Igual que {@link #validateNoOverlap(List)} pero con la ocupación ya cargada por el
     * caller (flujo automático: snapshot propio que ya excluye los eventos del preview).
     */
    public void validateNoOverlap(List<AllocationCandidate> candidates, List<OccupiedSlot> occupancy) {
        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        conflicts.addAll(databaseConflicts(candidates, occupancy));
        conflicts.addAll(internalConflicts(candidates));

        if (!conflicts.isEmpty()) {
            throw new ReassignConflictException(conflicts);
        }
    }

    /** Conflictos de cada candidato contra ocupación firme de BD ya cargada. */
    List<OccurrenceConflictDto> databaseConflicts(List<AllocationCandidate> candidates, List<OccupiedSlot> occupancy) {
        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        for (AllocationCandidate candidate : candidates) {
            LocalTime start = candidate.occurrence().startTime();
            LocalTime end = candidate.occurrence().endTime();
            for (OccupiedSlot occupied : occupancy) {
                if (!occupied.classroomId().equals(candidate.classroomId())) continue;
                if (!occupied.date().equals(candidate.occurrence().date())) continue;
                if (!TimeRanges.overlaps(start, end, occupied.startTime(), occupied.endTime())) continue;
                conflicts.add(new OccurrenceConflictDto(candidate.occurrence().occurrenceId(), candidate.occurrence().date(),
                        start, end, candidate.classroomId(), occupied.eventId(), occupied.allocationId()));
            }
        }
        return conflicts;
    }

    /**
     * Conflictos entre los propios candidatos: dos ocurrencias distintas cayendo en la
     * misma aula/fecha con franjas que se pisan. Nada persiste todavía → no hay
     * asignación real involucrada en el choque, {@code conflictingAllocationId} va null.
     * Dos ocurrencias del MISMO evento nunca son conflicto entre sí.
     */
    List<OccurrenceConflictDto> internalConflicts(List<AllocationCandidate> candidates) {
        List<OccurrenceConflictDto> conflicts = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            AllocationCandidate a = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                AllocationCandidate b = candidates.get(j);
                if (a.occurrence().eventId().equals(b.occurrence().eventId())) continue;
                if (!a.classroomId().equals(b.classroomId())) continue;
                if (!a.occurrence().date().equals(b.occurrence().date())) continue;
                LocalTime aStart = a.occurrence().startTime();
                LocalTime aEnd = a.occurrence().endTime();
                LocalTime bStart = b.occurrence().startTime();
                LocalTime bEnd = b.occurrence().endTime();
                if (!TimeRanges.overlaps(aStart, aEnd, bStart, bEnd)) continue;
                conflicts.add(new OccurrenceConflictDto(a.occurrence().occurrenceId(), a.occurrence().date(),
                        aStart, aEnd, a.classroomId(), b.occurrence().eventId(), null));
            }
        }
        return conflicts;
    }

    // ---------- estado de la ocurrencia ----------

    /** Ocurrencia ya ocurrida → no se puede modificar su asignación. */
    public void validateNotPast(OccurrenceSlotDto occurrence) {
        if (occurrence.isPast()) {
            throw new AllocationConflictException(
                    "Cannot modify allocation: occurrence on " + occurrence.date() + " has already taken place.");
        }
    }

    /** Evento sin ocurrencias futuras (todas ya sucedieron) → no se puede reasignar. */
    public void validateEventNotFinished(List<OccurrenceSlotDto> occurrences) {
        if (occurrences.stream().allMatch(OccurrenceSlotDto::isPast)) {
            throw new AllocationConflictException(
                    "Cannot reassign event: all its occurrences have already taken place.");
        }
    }

    /** Ocurrencia CANCELLED/SUSPENDED → no se le puede asignar aula. */
    public void validateAssignable(OccurrenceSlotDto occurrence) {
        if (!isAssignable(occurrence)) {
            throw new AllocationConflictException(
                    "Cannot assign classroom: occurrence " + occurrence.occurrenceId() + " is " + occurrence.status() + ".");
        }
    }

    /** Ocurrencia asignable: no CANCELLED ni SUSPENDED. */
    public boolean isAssignable(OccurrenceSlotDto occurrence) {
        return occurrence.status() != OccurrenceStatus.CANCELLED
                && occurrence.status() != OccurrenceStatus.SUSPENDED;
    }

    /** Ocurrencia aplicable a un lote: no pasada y asignable. */
    public boolean isApplicable(OccurrenceSlotDto occurrence) {
        return !occurrence.isPast() && isAssignable(occurrence);
    }

    // ---------- aulas ----------

    /**
     * Valida en batch que todas las aulas indicadas existan y estén disponibles
     * ({@code available == true}); inexistente o no disponible corta con 409 antes de
     * escribir nada.
     */
    public void validateClassroomsAvailable(Set<Integer> classroomIds) {
        Map<Integer, ClassroomResponseDto> classroomsById =
                Maps.byId(classroomService.findByIds(classroomIds), ClassroomResponseDto::id);
        for (Integer classroomId : classroomIds) {
            ClassroomResponseDto classroom = classroomsById.get(classroomId);
            if (classroom == null || !Boolean.TRUE.equals(classroom.available())) {
                throw new AllocationConflictException("El aula " + classroomId + " no existe o no está disponible.");
            }
        }
    }

    // ---------- pertenencia al preview (flujo automático) ----------

    /** La propuesta final no puede traer el mismo evento dos veces. */
    public void validateNoDuplicateEventIds(List<PreviewAllocationDto> allocations) {
        List<Long> eventIds = allocations.stream().map(PreviewAllocationDto::eventId).toList();
        if (new HashSet<>(eventIds).size() != eventIds.size()) {
            throw new AllocationConflictException("La propuesta final tiene eventos duplicados.");
        }
    }

    /** El total de los eventos de la propuesta final debe pertenecer al preview que se está confirmando. */
    public void validateAllocationsBelongToPreview(List<PreviewAllocationDto> allocations, Set<Long> previewEventIds) {
        Set<Long> foreign = foreignIds(allocations.stream().map(PreviewAllocationDto::eventId), previewEventIds);
        if (!foreign.isEmpty()) {
            throw new AllocationConflictException(
                    "los eventos " + foreign + " no pertenecen al preview indicado");
        }
    }

    /** {@code eventId} y todos los {@code currentAllocations} deben pertenecer al preview vigente. */
    public void validateBelongsToPreview(ValidateMoveRequestDto request, Set<Long> previewEventIds) {
        if (!previewEventIds.contains(request.eventId())) {
            throw new AllocationConflictException(
                    "el evento " + request.eventId() + " no pertenece al preview indicado");
        }
        Set<Long> foreign = foreignIds(
                request.currentAllocations().stream().map(PreviewAllocationDto::eventId), previewEventIds);
        if (!foreign.isEmpty()) {
            throw new AllocationConflictException(
                    "los eventos " + foreign + " de currentAllocations no pertenecen al preview indicado");
        }
    }

    private Set<Long> foreignIds(Stream<Long> ids, Set<Long> previewEventIds) {
        return ids.filter(id -> !previewEventIds.contains(id)).collect(Collectors.toSet());
    }

    // ---------- conflictos de validate-move (flujo automático) ----------

    /**
     * Conflictos del aula destino contra asignaciones firmes de BD ya cargadas: la
     * ocupación pasada por el caller ya excluye los eventos del preview (sus aulas quedaron
     * liberadas), así que solo puede chocar contra ocupación de eventos ajenos al preview.
     */
    public List<MoveConflictDto> moveDatabaseConflicts(Integer destination, Set<LocalDate> movedDates,
            LocalTime movedStart, LocalTime movedEnd, List<OccupiedSlot> databaseOccupancy) {
        List<MoveConflictDto> conflicts = new ArrayList<>();
        for (OccupiedSlot occupancy : databaseOccupancy) {
            if (!destination.equals(occupancy.classroomId())) continue;
            if (!movedDates.contains(occupancy.date())) continue;
            if (!TimeRanges.overlaps(movedStart, movedEnd, occupancy.startTime(), occupancy.endTime())) continue;
            conflicts.add(new MoveConflictDto(occupancy.date(), occupancy.startTime(), occupancy.endTime(),
                    destination, occupancy.eventId(), ConflictOrigin.DATABASE));
        }
        return conflicts;
    }

    /**
     * Conflictos contra el resto de la propuesta ajustada que viaja en el request: otros
     * ítems de {@code currentAllocations} (excluido el propio evento movido) que también
     * apuntan al aula destino, comparando fechas compartidas y franja horaria. Delega en
     * {@link #previewConflicts(Set, Set, LocalTime, LocalTime, List)}, el núcleo reusado
     * también por {@link #unresolvedConflicts}.
     */
    public List<MoveConflictDto> movePreviewConflicts(ValidateMoveRequestDto request,
            Map<Long, RecurringEventResponseDto> eventsById, Map<Long, List<LocalDate>> datesByEvent,
            Set<LocalDate> movedDates, LocalTime movedStart, LocalTime movedEnd) {
        List<ResolvedProposal> proposals = request.currentAllocations().stream()
                .filter(allocation -> !allocation.eventId().equals(request.eventId()))
                .map(allocation -> toResolvedProposal(allocation, eventsById, datesByEvent))
                .filter(Objects::nonNull)
                .toList();
        return previewConflicts(Set.of(request.classroomId()), movedDates, movedStart, movedEnd, proposals);
    }

    /** {@link PreviewAllocationDto} + horarios/fechas de su evento → {@link ResolvedProposal}, o null si el evento es ajeno al mapa cargado. */
    private ResolvedProposal toResolvedProposal(PreviewAllocationDto allocation,
            Map<Long, RecurringEventResponseDto> eventsById, Map<Long, List<LocalDate>> datesByEvent) {
        RecurringEventResponseDto event = eventsById.get(allocation.eventId());
        if (event == null) return null;
        return new ResolvedProposal(allocation.eventId(), allocation.classroomId(),
                datesByEvent.getOrDefault(allocation.eventId(), List.of()), event.startTime(), event.endTime());
    }

    // ---------- conflictos de unresolved (flujo automático, post-solve) ----------

    /**
     * Primer bloqueo por aula candidata para un evento inubicable del preview: para cada aula
     * candidata se busca el conflicto más temprano (por fecha), primero contra la ocupación
     * firme de BD y, si no hay, contra las propuestas ya resueltas del propio preview. Tope de
     * un conflicto por aula. Una aula candidata sin bloqueo hallado no debería darse (MEDIUM
     * domina SOFT, pero el solve puede cortar por tiempo) — se loguea y esa aula simplemente
     * no aparece en el resultado.
     */
    public List<MoveConflictDto> unresolvedConflicts(Set<Integer> candidateRoomIds, Set<LocalDate> dates,
            LocalTime start, LocalTime end, List<OccupiedSlot> databaseOccupancy,
            List<ResolvedProposal> resolvedProposals) {
        List<MoveConflictDto> conflicts = new ArrayList<>();
        for (Integer roomId : candidateRoomIds) {
            MoveConflictDto conflict = moveDatabaseConflicts(roomId, dates, start, end, databaseOccupancy).stream()
                    .min(Comparator.comparing(MoveConflictDto::date))
                    .orElse(null);
            if (conflict == null) {
                conflict = previewConflicts(Set.of(roomId), dates, start, end, resolvedProposals).stream()
                        .min(Comparator.comparing(MoveConflictDto::date))
                        .orElse(null);
            }
            if (conflict != null) {
                conflicts.add(conflict);
            } else {
                log.warn("Aula candidata {} sin conflicto hallado para evento inubicable del preview ({}-{})",
                        roomId, start, end);
            }
        }
        return conflicts;
    }

    /**
     * Núcleo reutilizable: TODOS los conflictos de un evento (fechas + franja) contra una
     * lista de propuestas ya resueltas, filtrando por aula candidata. Sin capar cantidad — cada
     * caller decide si se queda con todos ({@link #movePreviewConflicts}) o con el primero por
     * aula ({@link #unresolvedConflicts}).
     */
    private List<MoveConflictDto> previewConflicts(Set<Integer> candidateRoomIds, Set<LocalDate> dates,
            LocalTime start, LocalTime end, List<ResolvedProposal> resolvedProposals) {
        List<MoveConflictDto> conflicts = new ArrayList<>();
        for (ResolvedProposal proposal : resolvedProposals) {
            // classroomId null = fila unresolved de la propuesta ajustada: no ocupa aula, no bloquea.
            // (Set.of(...).contains(null) además tiraría NPE.)
            if (proposal.classroomId() == null || !candidateRoomIds.contains(proposal.classroomId())) continue;
            if (!TimeRanges.overlaps(start, end, proposal.startTime(), proposal.endTime())) continue;
            for (LocalDate date : proposal.dates()) {
                if (!dates.contains(date)) continue;
                conflicts.add(new MoveConflictDto(date, proposal.startTime(), proposal.endTime(),
                        proposal.classroomId(), proposal.eventId(), ConflictOrigin.PREVIEW));
            }
        }
        return conflicts;
    }

}
