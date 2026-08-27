package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface AllocationService {

    AllocationResponseDto findById(Long allocationId);

    List<AllocationResponseDto> findByDate(LocalDate date);

    List<AllocationResponseDto> allocate(AllocationCommand command);

    List<AllocationResponseDto> reallocate(AllocationCommand command);

    List<DeallocatedOccurrenceDto> deallocate(DeallocationCommand command);

    /**
     * Única puerta para el sync ASIGNACIONES de SysAcad (ver
     * .claude/docs/plan-sync-eventos-sysacad.md §4): {@code sysacad} sólo propone pares evento→aula ya
     * resueltos; la política de precedencia vive acá adentro, por ocurrencia:
     * <ul>
     *   <li>sin asignación existente → crea, {@code source=SYSACAD}, observación "Recuperado de SysAcad".</li>
     *   <li>asignación existente con {@code source=SYSACAD} → actualiza el aula, observación neutra
     *   "Actualizado por sync de SysAcad".</li>
     *   <li>asignación existente con cualquier otro origen (humano o import de Excel) → no se toca; WARN.</li>
     * </ul>
     * Las ocurrencias pasadas no se descartan (primer sync a mitad de año) y no se valida solapamiento
     * para este origen (se importa tal cual, ver supuestos-sync-horarios.md §3). Devuelve la cantidad de
     * ocurrencias afectadas (creadas+actualizadas).
     */
    int syncFromSysacad(List<AllocationItem> items);
}
