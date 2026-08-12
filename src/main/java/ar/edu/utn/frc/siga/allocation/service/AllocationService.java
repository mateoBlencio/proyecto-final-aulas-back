package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada pública de asignación de aulas a occurrences: tres verbos batch, ortogonales al
 * "sobre qué" ({@link ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget}) y al
 * "por qué" ({@code source}, que nunca lo decide el cliente — lo estampan las factories de
 * {@link AllocationCommand}). Toda operación, individual o en lote, es atómica.
 */
@NamedInterface("api")
public interface AllocationService {

    AllocationResponseDto findById(Long allocationId);

    List<AllocationResponseDto> findByDate(LocalDate date);

    /** Asigna aula. 409 si alguna occurrence del lote ya tiene asignación. */
    List<AllocationResponseDto> allocate(AllocationCommand command);

    /** Cambia el aula. Upsert: crea la asignación si no existía. */
    List<AllocationResponseDto> reallocate(AllocationCommand command);

    /** Libera el aula: borra la asignación de cada occurrence apuntada. Occurrences sin asignación se ignoran. */
    List<DeallocatedOccurrenceDto> deallocate(DeallocationCommand command);
}
