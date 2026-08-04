package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventAllocationRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.UpdateUniqueEventAllocationRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventAllocationResponseDto;

import java.util.List;

/**
 * Orquesta el alta/modificación atómica de un evento único junto con la asignación de su
 * aula: llama a {@code events::api} (crea/actualiza el evento y su occurrence, sin aula) y
 * a {@link AllocationService} (asigna/reasigna), dentro de la misma transacción — si el
 * aula no está disponible o hay solapamiento, no queda nada persistido.
 */
public interface UniqueEventAllocationService {

    /** Lista todos los eventos únicos con su aula, estado y sobrecupo. */
    List<UniqueEventAllocationResponseDto> findAll();

    /** Crea un evento único, su única occurrence y le asigna el aula indicada (source MANUAL), atómico. */
    UniqueEventAllocationResponseDto createUniqueEvent(CreateUniqueEventAllocationRequestDto dto);

    /**
     * Modifica un evento único existente y reasigna su aula (o la asigna si no tenía),
     * revalidando disponibilidad, solapamiento, capacidad y ventana horaria antes de
     * guardar. Rechaza si ya ocurrió, o si {@code id} no corresponde a un evento único (404).
     */
    UniqueEventAllocationResponseDto updateUniqueEvent(Long id, UpdateUniqueEventAllocationRequestDto dto);
}
