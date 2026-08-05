package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

/**
 * Vista de un {@code UniqueEvent} enriquecida con el estado de su única occurrence, el aula
 * asignada (null si aún no tiene) y el sobrecupo (0 si entra). {@code events} no conoce
 * aulas; esta composición vive en {@code allocation}.
 */
public record UniqueEventAllocationResponseDto(
        UniqueEventResponseDto event,
        OccurrenceStatus status,
        ClassroomResponseDto classroom,
        Integer overcrowdedBy,
        String observation
) {
}
