package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada pública de asignación de aulas a occurrences. Cada método es un "intent method":
 * {@code source} no es parámetro, lo estampa la implementación según el caso de uso
 * invocado (1 caso de uso → 1 source). Toda operación de asignación/reasignación —
 * individual o batch— es atómica.
 */
@NamedInterface("api")
public interface AllocationService {
    AllocationResponseDto findById(Long allocationId);

    /** Asigna aula (source MANUAL) a una occurrence puntual. Falla si ya tiene asignación, ya ocurrió, o no es asignable (CANCELLED/SUSPENDED). */
    AllocationResponseDto allocateManually(Long occurrenceId, AllocateOccurrenceRequestDto dto);

    /** Cambia el aula de una asignación existente (source MANUAL). Falla si la occurrence ya ocurrió. */
    AllocationResponseDto reallocate(Long allocationId, AllocateOccurrenceRequestDto dto);

    /** Reasigna varias asignaciones en una sola transacción (source MANUAL): si algún move choca o ya ocurrió, no se aplica ninguno. */
    List<AllocationResponseDto> batchReallocate(BatchReassignRequestDto dto);

    /** Asigna un aula a todas las occurrences futuras de un evento recurrente desde una fecha (source MANUAL), salteando las que ya ocurrieron. */
    List<AllocationResponseDto> allocateManuallyFromDate(AllocateFromDateRequestDto dto);

    /**
     * Igual que {@link #allocateManuallyFromDate}, pero con source IMPORTED e incluyendo
     * occurrences pasadas (carga masiva desde Excel). Devuelve la cantidad de allocations
     * aplicadas (no el DTO compuesto: el único caller productivo no lo necesita).
     */
    int importAllocationsFromDate(AllocateFromDateRequestDto dto);

    List<AllocationResponseDto> findByDate(LocalDate date);
}
