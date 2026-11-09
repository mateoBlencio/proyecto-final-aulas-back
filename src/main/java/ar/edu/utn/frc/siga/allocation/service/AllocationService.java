package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ReleasedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationLastRevisionDto;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.ReleaseCommand;
import ar.edu.utn.frc.siga.allocation.service.command.RestoreCommand;
import ar.edu.utn.frc.siga.allocation.service.command.RestoreResult;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeCommand;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeResult;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AllocationService {

    AllocationResponseDto findById(Long allocationId);

    Optional<Long> findClassroomIdByOccurrence(Long occurrenceId);

    List<AllocationResponseDto> findByDate(LocalDate date);

    List<AllocationResponseDto> findByOccurrenceIds(Collection<Long> occurrenceIds);

    List<OccurrenceSlotDto> resolveOccurrences(AllocationTarget target);

    List<AllocationResponseDto> allocate(AllocationCommand command);

    List<AllocationResponseDto> reallocate(AllocationCommand command);

    /**
     * Moves occurrences that already have a classroom. Rejects the whole batch with 409 if an
     * occurrence has no allocation. Used by PUT /v1/allocations; callers that may also create
     * allocations use reallocate.
     */
    List<AllocationResponseDto> reallocateExisting(AllocationCommand command);

    List<DeallocatedOccurrenceDto> deallocate(DeallocationCommand command);

    /**
     * Libera las ocurrencias del lote con motivo: borra sus asignaciones y las pasa a
     * {@code ROOM_RELEASED} en la misma transacción. Arrastra los espejos de cada ocurrencia con el
     * mismo motivo. Conserva el aula que tenían como {@code previousClassroomId}.
     */
    List<ReleasedOccurrenceDto> release(ReleaseCommand command);

    /**
     * Revierte la liberación: las ocurrencias liberadas vuelven a {@code NEEDS_ROOM}. No asigna aula
     * ni recrea espejos. Devuelve los ids revertidos.
     */
    List<Long> cancelRelease(DeallocationCommand command);

    /**
     * Única puerta para el sync ASIGNACIONES de SysAcad (ver
     * .claude/docs/plan-sync-eventos-sysacad.md §4): {@code sysacad} sólo propone pares evento→aula ya
     * resueltos; la política de precedencia vive acá adentro, por ocurrencia:
     * <ul>
     *   <li>sin asignación existente → crea, {@code source=SYSACAD}, observación "Recuperado de SysAcad".</li>
     *   <li>asignación existente con {@code source=SYSACAD} → actualiza el aula, observación neutra
     *   "Actualizado por sync de SysAcad", solo si el evento está en {@code eventIdsToUpdate} y la
     *   ocurrencia no es pasada; las pasadas no se tocan nunca.</li>
     *   <li>asignación existente con cualquier otro origen (humano o import de Excel) → no se toca; WARN.</li>
     * </ul>
     * Las ocurrencias pasadas sin asignación no se descartan (primer sync a mitad de año) y no se valida solapamiento
     * para este origen (se importa tal cual, ver supuestos-sync-horarios.md §3). Devuelve la cantidad de
     * ocurrencias afectadas (creadas+actualizadas).
     */
    int syncFromSysacad(List<AllocationItem> items, Set<Long> eventIdsToUpdate);

    /** Aula asignada por ocurrencia, solo de las que tienen asignación. No pasa por el composer. */
    Map<Long, Long> findClassroomIdsByOccurrences(Collection<Long> occurrenceIds);

    /**
     * Aplica un cambio de aula hecho directamente en SysAcad a las clases futuras principales de los
     * eventos: pisa cualquier origen y deja {@code source=SYSACAD}. Las pasadas y las liberadas no se tocan.
     * Si el aula está ocupada devuelve {@code Occupied} sin escribir nada. Publica los movimientos con
     * actor y origen {@code SYSACAD}.
     */
    SysacadDirectChangeResult applySysacadDirectChange(SysacadDirectChangeCommand command);

    /** No revisa el alcance por edificio: la autorización la hace quien llama. */
    RestoreResult restoreClassrooms(RestoreCommand command);

    /** Usuario y operación de la última revisión de auditoría de la asignación de la ocurrencia. */
    Optional<AllocationLastRevisionDto> findLastRevision(Long occurrenceId);
}
