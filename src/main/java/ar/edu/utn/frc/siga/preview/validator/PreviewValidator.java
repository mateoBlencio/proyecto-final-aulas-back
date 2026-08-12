package ar.edu.utn.frc.siga.preview.validator;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.RoomDate;
import ar.edu.utn.frc.siga.common.util.TimeSpan;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto.ConflictOrigin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compara la propuesta del preview automático (aún en memoria, sin persistir) contra sí misma
 * y contra el estado firme de BD: pertenencia al preview vigente y explicación de eventos
 * inubicables. No conoce el flujo manual ni escribe nada.
 */
@Slf4j
@Component
public class PreviewValidator {

    /**
     * Propuesta ya resuelta del preview (evento → aula candidata en ciertas fechas/franja),
     * desacoplada de {@link PreviewAllocationDto} para que el núcleo de comparación no dependa
     * del request HTTP.
     */
    public record ResolvedProposal(Long eventId, Integer classroomId, List<LocalDate> dates,
                                    LocalTime startTime, LocalTime endTime) implements TimeSpan {
    }

    /** El aula candidata y la franja del evento inubicable que se está probando contra la ocupación. */
    private record Moved(Integer classroomId, LocalTime startTime, LocalTime endTime) implements TimeSpan {
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

    private Set<Long> foreignIds(Stream<Long> ids, Set<Long> previewEventIds) {
        return ids.filter(id -> !previewEventIds.contains(id)).collect(Collectors.toSet());
    }

    // ---------- conflictos (flujo automático) ----------

    /**
     * Conflictos del aula destino contra asignaciones firmes de BD ya cargadas: la
     * ocupación pasada por el caller ya excluye los eventos del preview (sus aulas quedaron
     * liberadas), así que solo puede chocar contra ocupación de eventos ajenos al preview.
     */
    List<MoveConflictDto> moveDatabaseConflicts(Integer destination, Set<LocalDate> movedDates,
            LocalTime movedStart, LocalTime movedEnd, List<OccupiedSlot> databaseOccupancy) {
        return conflictsAgainst(destination, movedDates, movedStart, movedEnd, databaseOccupancy,
                occupied -> List.of(new RoomDate(occupied.classroomId(), occupied.date())),
                OccupiedSlot::eventId, ConflictOrigin.DATABASE);
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
                conflict = previewConflicts(roomId, dates, start, end, resolvedProposals).stream()
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
     * TODOS los conflictos de un evento (fechas + franja) contra las propuestas ya resueltas
     * del propio preview, filtrando por aula candidata. Una propuesta con
     * {@code classroomId == null} (fila unresolved) no ocupa aula: {@link #proposalKeys}
     * la deja sin claves, así que nunca entra en un bucket ni bloquea.
     */
    private List<MoveConflictDto> previewConflicts(Integer classroomId, Set<LocalDate> dates,
            LocalTime start, LocalTime end, List<ResolvedProposal> resolvedProposals) {
        return conflictsAgainst(classroomId, dates, start, end, resolvedProposals,
                PreviewValidator::proposalKeys, ResolvedProposal::eventId, ConflictOrigin.PREVIEW);
    }

    /**
     * Núcleo reutilizable de {@link #moveDatabaseConflicts} y {@link #previewConflicts}: el
     * aula candidata + franja del evento inubicable ({@code Moved}) contra cualquier conjunto
     * de ocupación que sepa dar sus propias claves aula+fecha y su {@code eventId}.
     */
    private <B extends TimeSpan> List<MoveConflictDto> conflictsAgainst(Integer classroomId, Set<LocalDate> dates,
            LocalTime start, LocalTime end, List<B> occupants,
            Function<B, List<RoomDate>> keysOf, Function<B, Long> eventIdOf,
            ConflictOrigin origin) {
        Moved moved = new Moved(classroomId, start, end);
        return Clashes.between(List.of(moved), m -> movedKeys(classroomId, dates),
                occupants, keysOf,
                (m, o) -> true,
                (m, o, key) -> new MoveConflictDto(key.date(), o.startTime(), o.endTime(),
                        key.classroomId(), eventIdOf.apply(o), origin));
    }

    private static List<RoomDate> movedKeys(Integer classroomId, Set<LocalDate> dates) {
        return dates.stream().map(date -> new RoomDate(classroomId, date)).toList();
    }

    private static List<RoomDate> proposalKeys(ResolvedProposal proposal) {
        if (proposal.classroomId() == null) return List.of();
        return proposal.dates().stream().map(date -> new RoomDate(proposal.classroomId(), date)).toList();
    }

}
