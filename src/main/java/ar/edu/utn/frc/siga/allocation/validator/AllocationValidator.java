package ar.edu.utn.frc.siga.allocation.validator;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReallocationConflictException;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.util.Clashes;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.common.util.RoomDate;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Centraliza todas las reglas de negocio de asignación de aulas, compartidas entre flujo
 * manual y automático (módulo {@code preview}, que la consume cross-módulo para el
 * pre-chequeo de solapamiento del confirm sobre su propio snapshot — ver
 * {@link #validateNoOverlap(List, List)}).
 */
@NamedInterface("api")
@Component
@RequiredArgsConstructor
public class AllocationValidator {

    private final ClassroomService classroomService;
    private final AllocationRepository allocationRepository;
    private final OccurrenceService occurrenceService;

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
                occurrenceService.findSlotsBetween(min, max),
                OccurrenceSlotDto::occurrenceId);

        List<OccupiedSlot> occupancy = allocationRepository.findByOccurrenceIdIn(slotByOccurrenceId.keySet())
                .stream()
                .filter(a -> !ownOccurrenceIds.contains(a.getOccurrenceId()))
                .map(a -> OccupiedSlot.from(a, slotByOccurrenceId.get(a.getOccurrenceId())))
                .toList();

        validateNoOverlap(future, occupancy);
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
            throw new ReallocationConflictException(conflicts);
        }
    }

    /** Conflictos de cada candidato contra ocupación firme de BD ya cargada. */
    List<OccurrenceConflictDto> databaseConflicts(List<AllocationCandidate> candidates, List<OccupiedSlot> occupancy) {
        return Clashes.between(candidates, AllocationValidator::candidateKey,
                occupancy, occupied -> List.of(new RoomDate(occupied.classroomId(), occupied.date())),
                (c, o) -> true,
                (c, o, key) -> new OccurrenceConflictDto(c.occurrence().occurrenceId(), key.date(),
                        c.startTime(), c.endTime(), c.classroomId(), o.eventId(), o.allocationId()));
    }

    /**
     * Conflictos entre los propios candidatos: dos ocurrencias distintas cayendo en la
     * misma aula/fecha con franjas que se pisan. Nada persiste todavía → no hay
     * asignación real involucrada en el choque, {@code conflictingAllocationId} va null.
     * Dos ocurrencias del MISMO evento nunca son conflicto entre sí.
     */
    List<OccurrenceConflictDto> internalConflicts(List<AllocationCandidate> candidates) {
        return Clashes.within(candidates, AllocationValidator::candidateKey,
                (a, b) -> !a.occurrence().eventId().equals(b.occurrence().eventId()),
                (a, b, key) -> new OccurrenceConflictDto(a.occurrence().occurrenceId(), key.date(),
                        a.startTime(), a.endTime(), a.classroomId(), b.occurrence().eventId(), null));
    }

    private static List<RoomDate> candidateKey(AllocationCandidate candidate) {
        return List.of(new RoomDate(candidate.classroomId(), candidate.occurrence().date()));
    }

    // ---------- estado de la ocurrencia ----------

    /** Ocurrencia ya ocurrida → no se puede modificar su asignación. */
    public void validateNotPast(OccurrenceSlotDto occurrence) {
        if (occurrence.isPast()) {
            throw new AllocationConflictException(
                    "No se puede modificar la asignación: la ocurrencia del " + occurrence.date() + " ya ocurrió.");
        }
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

}
