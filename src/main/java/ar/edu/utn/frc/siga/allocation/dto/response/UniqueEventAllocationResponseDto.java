package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

public record UniqueEventAllocationResponseDto(
        UniqueEventResponseDto event,
        OccurrenceStatus status,
        ReleaseReason releaseReason,
        String releaseNote,
        ClassroomResponseDto classroom,
        Integer overcrowdedBy,
        String observation
) {
}
